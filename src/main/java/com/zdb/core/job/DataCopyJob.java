package com.zdb.core.job;

import java.util.Arrays;
import java.util.Map;
import java.util.concurrent.CountDownLatch;

import com.zdb.core.util.K8SUtil;
import com.zdb.core.util.PodManager;

import io.fabric8.kubernetes.api.model.Container;
import io.fabric8.kubernetes.api.model.DoneablePod;
import io.fabric8.kubernetes.api.model.ObjectMeta;
import io.fabric8.kubernetes.api.model.PersistentVolumeClaimVolumeSource;
import io.fabric8.kubernetes.api.model.Pod;
import io.fabric8.kubernetes.api.model.PodList;
import io.fabric8.kubernetes.api.model.PodSpec;
import io.fabric8.kubernetes.api.model.Volume;
import io.fabric8.kubernetes.api.model.VolumeMount;
import io.fabric8.kubernetes.client.DefaultKubernetesClient;
import io.fabric8.kubernetes.client.KubernetesClientException;
import io.fabric8.kubernetes.client.Watch;
import io.fabric8.kubernetes.client.Watcher;
import io.fabric8.kubernetes.client.dsl.NonNamespaceOperation;
import io.fabric8.kubernetes.client.dsl.PodResource;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class DataCopyJob extends JobAdapter {
	public static String CP_NAME  = "cp-job";
	String copyPodName = null;
	public DataCopyJob(JobParameter param) { 
		super(param);
	}

	@Override
	public String getJobName() {
		return "Data copy";
	}

	@Override
	public JobKind getKind() {
		return JobKind.DATA_COPY;
	}

	@Override
	public void run() {
		setStatus(JobResult.RUNNING);
		
		String namespace = param.getNamespace();
		String stsName = param.getStatefulsetName();
		String sourcePvc = param.getSourcePvc();
		String targetPvc = param.getTargetPvc();
		copyPodName = CP_NAME + "-" + stsName;

		try (DefaultKubernetesClient kubernetesClient = K8SUtil.kubernetesClient();) {

			Pod pod = new Pod();
			pod.setKind("Pod");
			ObjectMeta metadata = new ObjectMeta();
			pod.setMetadata(metadata);
			metadata.setNamespace(namespace);
			metadata.setName(copyPodName);

			PodSpec podSpec = new PodSpec();
			podSpec.setRestartPolicy("Never");
			
			Container container = new Container();
			container.setImage("busybox");
			container.setCommand(Arrays.asList(new String[] { "sh", "-c", "rm -rf /data2/* && cp -R /data1/* /data2 && chown -R 1001:1001 /data2 && sleep 1" }));
			container.setImagePullPolicy("IfNotPresent");
			container.setName("busybox");
			
			//VolumeMount
			VolumeMount data1Volume = new VolumeMount();
			data1Volume.setName("data1");
			data1Volume.setMountPath("/data1");
			
			VolumeMount data2Volume = new VolumeMount();
			data2Volume.setName("data2");
			data2Volume.setMountPath("/data2");
			
			container.setVolumeMounts(Arrays.asList(new VolumeMount[] { data1Volume, data2Volume }));
			
			podSpec.setContainers(Arrays.asList(new Container[] { container }));
			
			
			// vlumes
			Volume data1 = new Volume();
			data1.setName("data1");
			data1.setPersistentVolumeClaim(new PersistentVolumeClaimVolumeSource(sourcePvc, false));

			Volume data2 = new Volume();
			data2.setName("data2");
			data2.setPersistentVolumeClaim(new PersistentVolumeClaimVolumeSource(targetPvc, false));
			
			podSpec.setVolumes(Arrays.asList(new Volume[] { data1, data2 }));
			
			pod.setSpec(podSpec);
			
			NonNamespaceOperation<Pod, PodList, DoneablePod, PodResource<Pod, DoneablePod>> pods = kubernetesClient.pods().inNamespace(namespace);
			
			Pod existPod = pods.withName(copyPodName).get();
			if(existPod != null) {
				pods.delete(existPod);
				
				Thread.sleep(2000);
			}

			final CountDownLatch latch = new CountDownLatch(2);
			
			Watcher<Pod> watcher = new Watcher<Pod>() {
				
				@Override
				public void eventReceived(Action action, Pod resource) {
					try {

						if (copyPodName.equals(resource.getMetadata().getName())) {
							progress(copyPodName + " - " + action + " | Status : " + resource.getStatus().getPhase());
							
							if (Action.DELETED == action) {
								progress("데이터 복사 Pod 삭제 완료. [" + resource.getMetadata().getName() + "]");
							} else if (Action.MODIFIED == action) {
								boolean isReady = PodManager.isReady(resource);
								if ("Succeeded".equals(resource.getStatus().getPhase())) {
									latch.countDown();
									setStatus(JobResult.OK);
								}
								if (isReady) {
									latch.countDown();

									progress("데이터 복사 Pod 생성 완료 (" + resource.getMetadata().getName() +")");
									setStatus(JobResult.RUNNING);
									
									copyProgress();
								}
								
							}
						}
					} catch (Exception e) {
						log.error(e.getMessage(), e);
					}
				}
				
				@Override
				public void onClose(KubernetesClientException cause) {
					if(cause != null) {
						log.error(cause.getMessage(), cause);
					} else {
						log.error("DataCopyJob closed...........");
					}
				}
				
			};
			Watch watch = kubernetesClient.pods().inNamespace(namespace).watch(watcher);
			
			Pod result = pods.create(pod);
			
			latch.await();
			
			existPod = pods.withName(copyPodName).get();
			if (existPod != null) {
				log.debug(copyPodName +" delete");
				pods.delete(existPod);
				
				Thread.sleep(2000);
			}
			
			watch.close();

			done(JobResult.OK, "데이터 복사 완료 (" + sourcePvc +" to "+ targetPvc + ")", null);
		} catch (Exception e) {
			e.printStackTrace();
			done(JobResult.ERROR, e.getMessage(), e);
		}
	}
	
	public void copyProgress() {
		{
			final DataCopyJob job = this;
			
			new Thread(new Runnable() {
				
				@Override
				public void run() {
					while (true) {
						try {
							Thread.sleep(1000 * 5);
							
							if(job.getStatus() == JobResult.OK || job.getStatus() == JobResult.ERROR) {
								break;
							}
							
							DefaultKubernetesClient kubernetesClient = K8SUtil.kubernetesClient();
							Map<String, DiskUsage> diskInfo = new CopyProgressChecker().getDiskInfo(kubernetesClient, param.getNamespace(), copyPodName);

							if (diskInfo != null && diskInfo.containsKey("/data1") && diskInfo.containsKey("/data2")) {
								DiskUsage data1 = diskInfo.get("/data1");
								double used1 = data1.getUsed();
								DiskUsage data2 = diskInfo.get("/data2");
								double used2 = data2.getUsed();

								double a = used2 / used1 * 100;
								double percent = (double) (Math.round(a * 100) / 100.0);
								String msg = used2 + " / " + used1 + "[" + percent + "%]";
								
								progress(msg);
								
								if (percent > 99.9) {
									break;
								}

							}
						} catch (Exception e) {
							log.error(e.getMessage(), e);
							break;
						} 
					}
				}
			}).start();
		}
	}
}
