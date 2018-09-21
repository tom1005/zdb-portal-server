package com.zdb.manager;

import java.util.Iterator;
import java.util.List;

import io.fabric8.kubernetes.api.model.Container;
import io.fabric8.kubernetes.api.model.EmptyDirVolumeSource;
import io.fabric8.kubernetes.api.model.PersistentVolumeClaimVolumeSource;
import io.fabric8.kubernetes.api.model.Volume;
import io.fabric8.kubernetes.api.model.VolumeMount;
import io.fabric8.kubernetes.api.model.extensions.DoneableStatefulSet;
import io.fabric8.kubernetes.api.model.extensions.StatefulSet;
import io.fabric8.kubernetes.api.model.extensions.StatefulSetBuilder;
import io.fabric8.kubernetes.api.model.extensions.StatefulSetList;
import io.fabric8.kubernetes.client.Config;
import io.fabric8.kubernetes.client.ConfigBuilder;
import io.fabric8.kubernetes.client.DefaultKubernetesClient;
import io.fabric8.kubernetes.client.dsl.MixedOperation;
import io.fabric8.kubernetes.client.dsl.RollableScalableResource;

public class UpdateStatefullset {

	public static void main(String[] args) {
		idToken = System.getProperty("token");
		masterUrl = System.getProperty("masterUrl");
		
		UpdateStatefullset sts = new UpdateStatefullset();

		String namespace = "ns-zdb-02";
		String stsName = "ns-zdb-02-uuu-mariadb-slave";//data-ns-zdb-02-0906-mariadb-slave-0
		
//		String namespace = "lawai-prod";
//		String stsName = "lawai-prod-lawai-mariadb-slave";//data-ns-zdb-02-0906-mariadb-slave-0

//		String namespace = "onspace-prod";
//		String stsName = "onspace-prod-db-mariadb-slave";//data-ns-zdb-02-0906-mariadb-slave-0

		String pvcName = "data-"+stsName+"-0";

		// 1. 기본 sts (data 볼륨 마운트)
		 sts.chageMountData(namespace, stsName, pvcName);
		
		// 2. data : empty {}, data_org : pvc 마운트
//		 sts.chageMount_EmptyData_DataOrg(namespace, stsName, pvcName);
		
		// 3.data : data pvc, backup : backup pvc (추가)
//		sts.chageMount_Data_Backup(namespace, stsName, "data-ns-zdb-02-0906-mariadb-slave-1", pvcName);
		
		// 3.data : data pvc, backup : backup pvc (추가)
//		sts.chageMount_EmptyData_Data1_Data2(namespace, stsName, "data-ns-zdb-02-0906-mariadb-slave-0", "data-ns-zdb-02-0906-mariadb-slave-1");
	}
	
	/**
	 * data : empty {}, data_org : pvc 마운트
	 * 
	 * @param namespace
	 * @param stsName
	 * @param pvcName
	 */
	public void chageMount_EmptyData_DataOrg(String namespace, String stsName, String pvcName) {
		try {
			DefaultKubernetesClient client = kubernetesClient();
			
			if(pvcName == null) {
				pvcName = "data-"+stsName+"-0";
			}
			
			MixedOperation<StatefulSet, StatefulSetList, DoneableStatefulSet, RollableScalableResource<StatefulSet, DoneableStatefulSet>> statefulSets = client.inNamespace(namespace).apps().statefulSets();
			StatefulSet statefulSet = statefulSets.withName(stsName).get();
			
			if (statefulSet.getSpec().getVolumeClaimTemplates().size() > 0) {
				statefulSet.getSpec().getVolumeClaimTemplates().remove(0);
			}
			
			int mariadbContaineerIndex = -1;
			List<Container> containers = statefulSet.getSpec().getTemplate().getSpec().getContainers();
			for (Container container : containers) {
				if(container.getName().equals("mariadb")) {
					mariadbContaineerIndex++;
					break;
				}
			}
			
			List<VolumeMount> volumeMounts = statefulSet.getSpec().getTemplate().getSpec().getContainers().get(mariadbContaineerIndex).getVolumeMounts();
			for (Iterator<VolumeMount> iterator = volumeMounts.iterator(); iterator.hasNext();) {
				VolumeMount v = iterator.next();
				
				if(v.getName().equals("config") || v.getName().equals("data")) {
					continue;
				}
				iterator.remove();
			}
			
			List<Volume> volumes = statefulSet.getSpec().getTemplate().getSpec().getVolumes();
			for (Iterator<Volume> iterator = volumes.iterator(); iterator.hasNext();) {
				Volume v = iterator.next();
				
				if(v.getName().equals("config")) {
					continue;
				}
				iterator.remove();
			}
			
			StatefulSetBuilder stsBuilder = new StatefulSetBuilder(statefulSet);
			
			Volume dataVolume = new Volume();
			dataVolume.setName("data-org");
			dataVolume.setPersistentVolumeClaim(new PersistentVolumeClaimVolumeSource(pvcName, null));
			
			Volume emptyVolume = new Volume();
			emptyVolume.setName("data");
			emptyVolume.setEmptyDir(new EmptyDirVolumeSource());
			
			VolumeMount volumeMount = new VolumeMount("/bitnami/data_org", "data-org", null, null);
			
			StatefulSet newSts = stsBuilder
					.editSpec().editTemplate().editSpec().addToVolumes(emptyVolume).endSpec().endTemplate().endSpec()
					.editSpec().editTemplate().editSpec().addToVolumes(dataVolume).endSpec().endTemplate().endSpec()
					.editSpec().editTemplate().editSpec().editContainer(mariadbContaineerIndex).addToVolumeMounts(volumeMount).endContainer().endSpec().endTemplate().endSpec()
					.editMetadata().withResourceVersion(null).withUid(null).withCreationTimestamp(null).endMetadata()
					.build();
			
			statefulSets.withName(statefulSet.getMetadata().getName()).delete();
			statefulSets.createOrReplace(newSts);
			
		} catch (Exception e) {
			e.printStackTrace();
		}
	}
	/**
	 * data : data pvc, 
	 * backup : backup pvc (추가)
	 * 
	 * @param namespace
	 * @param stsName
	 * @param pvcName
	 */
	public void chageMount_Data_Backup(String namespace, String stsName, String pvcName, String backupPVCName) {
		try {
			DefaultKubernetesClient client = kubernetesClient();
			
			if(pvcName == null) {
				pvcName = "data-"+stsName+"-0";
			}
			
			MixedOperation<StatefulSet, StatefulSetList, DoneableStatefulSet, RollableScalableResource<StatefulSet, DoneableStatefulSet>> statefulSets = client.inNamespace(namespace).apps().statefulSets();
			StatefulSet statefulSet = statefulSets.withName(stsName).get();
			
			if (statefulSet.getSpec().getVolumeClaimTemplates().size() > 0) {
				statefulSet.getSpec().getVolumeClaimTemplates().remove(0);
			}
			
			int mariadbContaineerIndex = -1;
			List<Container> containers = statefulSet.getSpec().getTemplate().getSpec().getContainers();
			for (Container container : containers) {
				if(container.getName().equals("mariadb")) {
					mariadbContaineerIndex++;
					break;
				}
			}
			
			List<VolumeMount> volumeMounts = statefulSet.getSpec().getTemplate().getSpec().getContainers().get(mariadbContaineerIndex).getVolumeMounts();
			for (Iterator<VolumeMount> iterator = volumeMounts.iterator(); iterator.hasNext();) {
				VolumeMount v = iterator.next();
				
				if(v.getName().equals("config") || v.getName().equals("data")) {
					continue;
				}
				iterator.remove();
			}
			
			List<Volume> volumes = statefulSet.getSpec().getTemplate().getSpec().getVolumes();
			for (Iterator<Volume> iterator = volumes.iterator(); iterator.hasNext();) {
				Volume v = iterator.next();
				
				if(v.getName().equals("config")) {
					continue;
				}
				iterator.remove();
			}
			
			StatefulSetBuilder stsBuilder = new StatefulSetBuilder(statefulSet);
			
			Volume dataVolume = new Volume();
			dataVolume.setName("data");
			dataVolume.setPersistentVolumeClaim(new PersistentVolumeClaimVolumeSource(pvcName, null));
			
			Volume backupVolume = new Volume();
			backupVolume.setName("backup");
			backupVolume.setPersistentVolumeClaim(new PersistentVolumeClaimVolumeSource(backupPVCName, null));
			
			VolumeMount volumeMount = new VolumeMount("/bitnami/backup", "backup", null, null);
			
			StatefulSet newSts = stsBuilder
					.editSpec().editTemplate().editSpec().addToVolumes(backupVolume).endSpec().endTemplate().endSpec()
					.editSpec().editTemplate().editSpec().addToVolumes(dataVolume).endSpec().endTemplate().endSpec()
					.editSpec().editTemplate().editSpec().editContainer(mariadbContaineerIndex).addToVolumeMounts(volumeMount).endContainer().endSpec().endTemplate().endSpec()
					.editMetadata().withResourceVersion(null).withUid(null).withCreationTimestamp(null).endMetadata()
					.build();
			
			statefulSets.withName(statefulSet.getMetadata().getName()).delete();
			statefulSets.createOrReplace(newSts);
			
		} catch (Exception e) {
			e.printStackTrace();
		}
	}
	
	/**
	 * data : data pvc, 
	 * backup : backup pvc (추가)
	 * 
	 * @param namespace
	 * @param stsName
	 * @param pvcName
	 */
	public void chageMount_EmptyData_Data1_Data2(String namespace, String stsName, String data1PVCName, String data2PVCName) {
		try {
			DefaultKubernetesClient client = kubernetesClient();
			
			MixedOperation<StatefulSet, StatefulSetList, DoneableStatefulSet, RollableScalableResource<StatefulSet, DoneableStatefulSet>> statefulSets = client.inNamespace(namespace).apps().statefulSets();
			StatefulSet statefulSet = statefulSets.withName(stsName).get();
			
			if (statefulSet.getSpec().getVolumeClaimTemplates().size() > 0) {
				statefulSet.getSpec().getVolumeClaimTemplates().remove(0);
			}
			
			int mariadbContaineerIndex = -1;
			List<Container> containers = statefulSet.getSpec().getTemplate().getSpec().getContainers();
			for (Container container : containers) {
				if(container.getName().equals("mariadb")) {
					mariadbContaineerIndex++;
					break;
				}
			}
			
			List<VolumeMount> volumeMounts = statefulSet.getSpec().getTemplate().getSpec().getContainers().get(mariadbContaineerIndex).getVolumeMounts();
			for (Iterator<VolumeMount> iterator = volumeMounts.iterator(); iterator.hasNext();) {
				VolumeMount v = iterator.next();
				
				if(v.getName().equals("config") || v.getName().equals("data")) {
					continue;
				}
				iterator.remove();
			}
			
			List<Volume> volumes = statefulSet.getSpec().getTemplate().getSpec().getVolumes();
			for (Iterator<Volume> iterator = volumes.iterator(); iterator.hasNext();) {
				Volume v = iterator.next();
				
				if(v.getName().equals("config")) {
					continue;
				}
				iterator.remove();
			}
			
			StatefulSetBuilder stsBuilder = new StatefulSetBuilder(statefulSet);
			
			Volume data1Volume = new Volume();
			data1Volume.setName("data1");
			data1Volume.setPersistentVolumeClaim(new PersistentVolumeClaimVolumeSource(data1PVCName, null));
			
			Volume data2Volume = new Volume();
			data2Volume.setName("data2");
			data2Volume.setPersistentVolumeClaim(new PersistentVolumeClaimVolumeSource(data2PVCName, null));
			
			Volume emptyVolume = new Volume();
			emptyVolume.setName("data");
			emptyVolume.setEmptyDir(new EmptyDirVolumeSource());
			
			VolumeMount volume1Mount = new VolumeMount("/bitnami/data1", "data1", null, null);
			VolumeMount volume2Mount = new VolumeMount("/bitnami/data2", "data2", null, null);
			
			StatefulSet newSts = stsBuilder
					.editSpec().editTemplate().editSpec().addToVolumes(emptyVolume).endSpec().endTemplate().endSpec()
					.editSpec().editTemplate().editSpec().addToVolumes(data1Volume).endSpec().endTemplate().endSpec()
					.editSpec().editTemplate().editSpec().addToVolumes(data2Volume).endSpec().endTemplate().endSpec()
					.editSpec().editTemplate().editSpec().editContainer(mariadbContaineerIndex).addToVolumeMounts(volume1Mount).endContainer().endSpec().endTemplate().endSpec()
					.editSpec().editTemplate().editSpec().editContainer(mariadbContaineerIndex).addToVolumeMounts(volume2Mount).endContainer().endSpec().endTemplate().endSpec()
					.editMetadata().withResourceVersion(null).withUid(null).withCreationTimestamp(null).endMetadata()
					.build();
			
			statefulSets.withName(statefulSet.getMetadata().getName()).delete();
			statefulSets.createOrReplace(newSts);
			
		} catch (Exception e) {
			e.printStackTrace();
		}
	}
	
	
	/**
	 * 기본 sts (data 볼륨 마운트)
	 * 
	 * VolumeClaimTemplates 삭제
	 * Volume 추가
	 * 
	 * @param namespace
	 * @param stsName
	 */
	public void chageMountData(String namespace, String stsName, String pvcName) {
		try {
			DefaultKubernetesClient client = kubernetesClient();
			
			if(pvcName == null) {
				pvcName = "data-"+stsName+"-0";
			}
			
			MixedOperation<StatefulSet, StatefulSetList, DoneableStatefulSet, RollableScalableResource<StatefulSet, DoneableStatefulSet>> statefulSets = client.inNamespace(namespace).apps().statefulSets();
			StatefulSet statefulSet = statefulSets.withName(stsName).get();
			
			
			Volume dataVolume = new Volume();
			dataVolume.setName("data");
			dataVolume.setPersistentVolumeClaim(new PersistentVolumeClaimVolumeSource(pvcName, null));
			
			List<Volume> volumes = statefulSet.getSpec().getTemplate().getSpec().getVolumes();
			for (Iterator<Volume> iterator = volumes.iterator(); iterator.hasNext();) {
				Volume v = iterator.next();
				
				if(!v.getName().equals("config")) {
					iterator.remove();
				}
			}
			
			int mariadbContaineerIndex = -1;
			List<Container> containers = statefulSet.getSpec().getTemplate().getSpec().getContainers();
			for (Container container : containers) {
				if(container.getName().equals("mariadb")) {
					mariadbContaineerIndex++;
					break;
				}
			}
			
			List<VolumeMount> volumeMounts = statefulSet.getSpec().getTemplate().getSpec().getContainers().get(mariadbContaineerIndex).getVolumeMounts();
			for (Iterator<VolumeMount> iterator = volumeMounts.iterator(); iterator.hasNext();) {
				VolumeMount v = iterator.next();
				
				if(v.getName().equals("config") || v.getName().equals("data")) {
					continue;
				}
				iterator.remove();
			}
			
			if(statefulSet.getSpec().getVolumeClaimTemplates().size() >0 ) {
				statefulSet.getSpec().getVolumeClaimTemplates().remove(0);
			}
			
			StatefulSetBuilder stsBuilder = new StatefulSetBuilder(statefulSet);
 
//			int mariadbContaineerIndex = -1;
//			List<Container> containers = statefulSet.getSpec().getTemplate().getSpec().getContainers();
//			for (Container container : containers) {
//				if(container.getName().equals("mariadb")) {
//					mariadbContaineerIndex++;
//					break;
//				}
//			}
			
//			VolumeMount volumeMount = new VolumeMount("/bitnami/backup", "backup", null, null);
			
			StatefulSet newSts = stsBuilder
					.editSpec().editTemplate().editSpec().addToVolumes(dataVolume).endSpec().endTemplate().endSpec()
//					.editSpec().editTemplate().editSpec().editContainer(mariadbContaineerIndex).addToVolumeMounts(volumeMount).endContainer().endSpec().endTemplate().endSpec()
					.editMetadata().withResourceVersion(null).withUid(null).withCreationTimestamp(null).endMetadata()
					
					.build();
			
			
			statefulSets.withName(statefulSet.getMetadata().getName()).delete();
			statefulSets.createOrReplace(newSts);
			
		} catch (Exception e) {
			e.printStackTrace();
		}
	}
	
	
//public void aaa () {
//	try {
//		DefaultKubernetesClient client = kubernetesClient();
//		String namespace = "ns-zdb-02";
//		MixedOperation<StatefulSet, StatefulSetList, DoneableStatefulSet, RollableScalableResource<StatefulSet, DoneableStatefulSet>> statefulSets = client.inNamespace(namespace).apps().statefulSets();
//		StatefulSet statefulSet = statefulSets.withName("ns-zdb-02-0906-mariadb-master").get();
////		StatefulSet statefulSet = statefulSets.withName("ns-zdb-01-account-mariadb").get();
//		
////		Volume v = new Volume();
//		StatefulSetBuilder stsBuilder = new StatefulSetBuilder(statefulSet);
//		// # replica count
////		StatefulSet newSts = stsBuilder.editSpec().withReplicas(0).endSpec().build();
////		statefulSets.createOrReplace(newSts);
//		
//		Volume volume = new Volume();
//		volume.setName("backup");
//		volume.setPersistentVolumeClaim(new PersistentVolumeClaimVolumeSource("data-ns-zdb-02-0906-mariadb-master-0", null));
////		
//		Volume emptyVolume = new Volume();
//		emptyVolume.setName("data");
//		emptyVolume.setEmptyDir(new EmptyDirVolumeSource());
//		
//		List<Volume> volumes = statefulSet.getSpec().getTemplate().getSpec().getVolumes();
//		for (Iterator iterator = volumes.iterator(); iterator.hasNext();) {
//			Volume v = (Volume) iterator.next();
//			
//			if(v.getName().equals("data")) {
//				iterator.remove();
//			}
//			
//			if(v.getName().equals("backup")) {
//				iterator.remove();
//			}
//		}
//
////		  "spec": {
////		    "template": {
////		      "spec": {
////		        "volumes": [
////		          {
////		            "name": "config",
////		            "configMap": {
////		              "name": "ns-zdb-02-0906-mariadb-master",
////		              "defaultMode": 420
////		            }
////		          },
////		          {
////		            "name": "data",
////		            "emptyDir": {}
////		          },
////		          {
////		            "name": "backup",
////		            "persistentVolumeClaim": {
////		              "claimName": "data-ns-zdb-02-0906-mariadb-master-0"
////		            }
////		          }
////		        ],
//		int mariadbContaineerIndex = -1;
//		List<Container> containers = statefulSet.getSpec().getTemplate().getSpec().getContainers();
//		for (Container container : containers) {
//			if(container.getName().equals("mariadb")) {
//				mariadbContaineerIndex++;
//				break;
//			}
//		}
//		
//		VolumeMount volumeMount = new VolumeMount("/bitnami/backup", "backup", null, null);
//		
//		StatefulSet newSts = stsBuilder
//				.editSpec().editTemplate().editSpec().addToVolumes(emptyVolume).endSpec().endTemplate().endSpec()
//				.editSpec().editTemplate().editSpec().addToVolumes(volume).endSpec().endTemplate().endSpec()
//				.editSpec().editTemplate().editSpec().editContainer(mariadbContaineerIndex).addToVolumeMounts(volumeMount).endContainer().endSpec().endTemplate().endSpec()
//				.editMetadata().withResourceVersion(null).withUid(null).withCreationTimestamp(null).endMetadata()
//				
//				.build();
//		
//		if(newSts.getSpec().getVolumeClaimTemplates().size() >0 ) {
//			newSts.getSpec().getVolumeClaimTemplates().remove(0);
//		}
//		
//		statefulSets.withName(statefulSet.getMetadata().getName()).delete();
//		
//		statefulSets.createOrReplace(newSts);
//		
//	} catch (Exception e) {
//		e.printStackTrace();
//	}
//}
	
	
	DefaultKubernetesClient client = null;
	String clusterName = null;
	static String idToken = null;
	static String masterUrl = null;
	
	public DefaultKubernetesClient kubernetesClient() throws Exception {
		if(client !=null) {
			return client;
		}
		


		if (idToken == null || masterUrl == null) {
			System.err.println("VM arguments 설정 후 실행 하세요...\n-DmasterUrl=xxx.xxx.xxx.xx:12345 -Dtoken=xxxxxx");
			System.exit(-1);
		}

		System.setProperty(Config.KUBERNETES_AUTH_TRYSERVICEACCOUNT_SYSTEM_PROPERTY, "false");

		Config config = new ConfigBuilder().withMasterUrl(masterUrl).withOauthToken(idToken).withTrustCerts(true).withWatchReconnectLimit(-1).build();
		client = new DefaultKubernetesClient(config);

		return client;
	}

}
