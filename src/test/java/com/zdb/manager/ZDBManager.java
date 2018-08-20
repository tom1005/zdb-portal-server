package com.zdb.manager;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Date;
import java.util.Iterator;
import java.util.List;
import java.util.ListIterator;
import java.util.Map;

import com.zdb.core.domain.CommonConstants;

import io.fabric8.kubernetes.api.model.ConfigMap;
import io.fabric8.kubernetes.api.model.ContainerStatus;
import io.fabric8.kubernetes.api.model.Event;
import io.fabric8.kubernetes.api.model.LoadBalancerIngress;
import io.fabric8.kubernetes.api.model.Namespace;
import io.fabric8.kubernetes.api.model.Node;
import io.fabric8.kubernetes.api.model.NodeCondition;
import io.fabric8.kubernetes.api.model.NodeSystemInfo;
import io.fabric8.kubernetes.api.model.PersistentVolumeClaim;
import io.fabric8.kubernetes.api.model.Pod;
import io.fabric8.kubernetes.api.model.PodCondition;
import io.fabric8.kubernetes.api.model.PodStatus;
import io.fabric8.kubernetes.api.model.Quantity;
import io.fabric8.kubernetes.api.model.Secret;
import io.fabric8.kubernetes.api.model.Service;
import io.fabric8.kubernetes.api.model.extensions.Deployment;
import io.fabric8.kubernetes.api.model.extensions.DeploymentStatus;
import io.fabric8.kubernetes.api.model.extensions.Ingress;
import io.fabric8.kubernetes.api.model.extensions.IngressRule;
import io.fabric8.kubernetes.api.model.extensions.ReplicaSet;
import io.fabric8.kubernetes.api.model.extensions.ReplicaSetStatus;
import io.fabric8.kubernetes.api.model.extensions.StatefulSet;
import io.fabric8.kubernetes.api.model.extensions.StatefulSetStatus;
import io.fabric8.kubernetes.client.Config;
import io.fabric8.kubernetes.client.ConfigBuilder;
import io.fabric8.kubernetes.client.DefaultKubernetesClient;

public class ZDBManager {

	public static void main(String[] args) throws Exception {
		nodes() ;
		namespaces() ;
		statefulset();
		deployments();
		replicasets();
		services();
		pods();
		pvcs();
		configmaps();
		secrets();
		ingresses();
		events();
		
	}
	
	public static void namespaces() throws Exception {
		List<Namespace> namespaces = kubernetesClient().inAnyNamespace().namespaces().withLabel(CommonConstants.ZDB_LABEL, "true").list().getItems();
		
		System.out.println(cc("=", 170));
		System.out.println("#Namespaces");
		System.out.println(cc("=", 170));
		System.out.println(b("Name", 15) + "  " + b("Labels", 70) + "  " + b("Age", 15));
		System.out.println(cc("=", 170));
		
		for (Namespace n : namespaces) {
			String namespace = n.getMetadata().getName();
			
			String creationTimestamp = n.getMetadata().getCreationTimestamp();
			creationTimestamp = creationTimestamp.replace("T", " ").replace("Z", "");
			creationTimestamp = elapsedTime(creationTimestamp);
			
			StringBuffer sb = new StringBuffer();
			Map<String, String> labels = n.getMetadata().getLabels();
			if (labels != null) {
				for (Iterator<String> iterator = labels.keySet().iterator(); iterator.hasNext();) {
					String key = iterator.next();

					sb.append(key).append(":").append(labels.get(key)).append(" ");
				}
			}
			
			System.out.println(b(namespace, 15) + "  " + b(sb.toString(), 70) +"  " + creationTimestamp);
		}
	}

	public static List<String> getNamespacesList() throws Exception {
		List<Namespace> namespaces = kubernetesClient().inAnyNamespace().namespaces().withLabel(CommonConstants.ZDB_LABEL, "true").list().getItems();
		List<String> nsList = new ArrayList<>();
		for (Namespace n : namespaces) {
			String name = n.getMetadata().getName();
			nsList.add(name);
		}
		
		return nsList;
	}
	public static void statefulset() throws Exception {
		List<String> nsList = getNamespacesList();
		List<StatefulSet> items = kubernetesClient().apps().statefulSets().list().getItems();
		
		System.out.println(cc("=", 170));
		System.out.println("#Statefulset");
		System.out.println(cc("=", 170));
		System.out.println(b("Namespace", 15) + "  " +b("Name", 40) + "  " + b("Pods", 6) + "  " + b("Age", 15));
		System.out.println(cc("=", 170));
		
		
		for (StatefulSet statefulSet : items) {
			String namespace = statefulSet.getMetadata().getNamespace();
			if(!nsList.contains(namespace)) {
				continue;
			}
			
			String name = statefulSet.getMetadata().getName();
			String creationTimestamp = statefulSet.getMetadata().getCreationTimestamp();
			creationTimestamp = creationTimestamp.replace("T", " ").replace("Z", "");
			creationTimestamp = elapsedTime(creationTimestamp);
			
			StatefulSetStatus status = statefulSet.getStatus();
			
			//readyReplicas=1, replicas=1
			int replicas = status.getReplicas().intValue();
			int readyReplicas = status.getReadyReplicas().intValue();
			
			System.out.println(b(namespace, 15) + "  " +b(name, 50) + "  " + b(readyReplicas+ "/"+replicas, 6) + "  " + b(creationTimestamp, 15));
		}
	}
	
	public static void events() throws Exception {
		//Event(apiVersion=v1, count=27297, 
		// - firstTimestamp=2018-08-07T13:59:56Z, 
		//involvedObject=ObjectReference(apiVersion=v1, fieldPath=null, 
		// - kind=Pod, name=kubernetes-dashboard-78b9dcc677-qrbvc, namespace=kube-system, uid=45325b92-8bff-11e8-a7fe-5664a44d0e47, ), 
		// kind=Event, 
		// - lastTimestamp=2018-08-13T03:24:35Z, 
		// - message=0/9 nodes are available: 7 MatchNodeSelector, 7 PodToleratesNodeTaints., 
		
		// metadata=ObjectMeta(annotations=null, clusterName=null, creationTimestamp=2018-08-07T14:04:33Z, deletionGracePeriodSeconds=null,
		// deletionTimestamp=null, finalizers=[], generateName=null, generation=null, initializers=null, labels=null, name=kubernetes-dashboard-78b9dcc677-qrbvc.15489ee903ef8ac2, namespace=kube-system, 
		 // ownerReferences=[], resourceVersion=4814019, uid=cfc127a8-9a4a-11e8-82f4-1e84ac9699c4, additionalProperties={}), 
		
		// - reason=FailedScheduling, source=EventSource(component=default-scheduler, host=null, additionalProperties={}), 
		// - type=Warning, 
		// additionalProperties={reportingInstance=, eventTime=null, reportingComponent=})
		
		List<String> nsList = getNamespacesList();
		List<Event> items = kubernetesClient().events().list().getItems();
		
		System.out.println(cc("=", 290));
		System.out.println("#Event");
		System.out.println(cc("=", 290));
		System.out.println(b("Type", 12) + b("Namespace", 15) + "  " +b("Kind", 22) + "  " +b("Name", 55) + "  " +b("First", 22) + "  " +b("Last", 22) + "  " + b("Reason", 20) + "  " + b("Message", 100));
		System.out.println(cc("=", 290));
		
		
		for (Event event : items) {
			String namespace = event.getInvolvedObject().getNamespace();
//			if(!nsList.contains(namespace)) {
//				continue;
//			}
			
			String kind = event.getInvolvedObject().getKind();
			String name = event.getInvolvedObject().getName();
			
			String firstTimestamp = event.getFirstTimestamp();
			firstTimestamp = firstTimestamp.replace("T", " ").replace("Z", "");
//			firstTimestamp = elapsedTime(firstTimestamp);
			
			String lastTimestamp = event.getLastTimestamp();
			lastTimestamp = lastTimestamp.replace("T", " ").replace("Z", "");
//			lastTimestamp = elapsedTime(lastTimestamp);
			
			String message = event.getMessage();
			String reason = event.getReason();
			String type = event.getType();
			
			
			System.out.println(b(type, 12) + b(namespace, 15) + "  " +b(kind, 22) + "  " +b(name, 55) + "  " +b(firstTimestamp, 22) + "  " +b(lastTimestamp, 22) + "  " + b(reason, 20)  + "  " + b(message, 100));
		}
	}
	
	public static void deployments() throws Exception {

		List<String> nsList = getNamespacesList();
		List<Deployment> items = kubernetesClient().extensions().deployments().list().getItems();

		System.out.println(cc("=", 170));
		System.out.println("#Deployment");
		System.out.println(cc("=", 170));
		System.out.println(b("Namespace", 15) + "  " +b("Name", 40) + "  " + b("Pods", 6) + "  " + b("Age", 15));
		System.out.println(cc("=", 170));
		
		
		for (Deployment deployment : items) {
			String namespace = deployment.getMetadata().getNamespace();
			if(!nsList.contains(namespace)) {
				continue;
			}
			
			String name = deployment.getMetadata().getName();
			String creationTimestamp = deployment.getMetadata().getCreationTimestamp();
			creationTimestamp = creationTimestamp.replace("T", " ").replace("Z", "");
			creationTimestamp = elapsedTime(creationTimestamp);
			
			DeploymentStatus status = deployment.getStatus();
			
			//readyReplicas=1, replicas=1
			int replicas = status.getReplicas().intValue();
			int readyReplicas = status.getReadyReplicas().intValue();
			
			System.out.println(b(namespace, 15) + "  " +b(name, 40) + "  " + b(readyReplicas+ "/"+replicas, 6) + "  " + creationTimestamp);
		}
	
	}
	
	public static void replicasets() throws Exception {


		List<String> nsList = getNamespacesList();
		List<ReplicaSet> items = kubernetesClient().extensions().replicaSets().list().getItems();

		System.out.println(cc("=", 170));
		System.out.println("#ReplicaSet");
		System.out.println(cc("=", 170));
		System.out.println(b("Namespace", 15) + "  " +b("Name", 40) + "  " + b("Pods", 6) + "  " + b("Age", 15));
		System.out.println(cc("=", 170));
		
		
		for (ReplicaSet replicaSet : items) {
			String namespace = replicaSet.getMetadata().getNamespace();
			if(!nsList.contains(namespace)) {
				continue;
			}
			
			String name = replicaSet.getMetadata().getName();
			String creationTimestamp = replicaSet.getMetadata().getCreationTimestamp();
			creationTimestamp = creationTimestamp.replace("T", " ").replace("Z", "");
			creationTimestamp = elapsedTime(creationTimestamp);
			
			ReplicaSetStatus status = replicaSet.getStatus();
			
			//readyReplicas=1, replicas=1
			int replicas = status.getReplicas().intValue();
			int readyReplicas = status.getReadyReplicas().intValue();
			
			System.out.println(b(namespace, 15) + "  " +b(name, 40) + "  " + b(readyReplicas+ "/"+replicas, 6) + "  " + b(creationTimestamp, 15));
		}
	
	
	}

	public static void pvcs() throws Exception {
		List<String> nsList = getNamespacesList();
		System.out.println(cc("=", 170));
		System.out.println("#PersistentVolumeClaims");
		System.out.println(cc("=", 170));
		System.out.println(b("Namespace", 15) + "  " +b("Name", 50) + "  " + b("Status", 6) + "  " + b("Volume", 41)+ "  " + b("Capacity", 8)+ "  " + b("Access Modes", 14)+ "  " + b("Storage Class", 17)+ "  " + b("Age", 15));
		System.out.println(cc("=", 170));
		
		List<PersistentVolumeClaim> items = kubernetesClient().persistentVolumeClaims().list().getItems();
		
		for (PersistentVolumeClaim pvc : items) {
			String namespace = pvc.getMetadata().getNamespace();
			if(!nsList.contains(namespace)) {
				continue;
			}
			
			String name = pvc.getMetadata().getName();
			String creationTimestamp = pvc.getMetadata().getCreationTimestamp();
			creationTimestamp = creationTimestamp.replace("T", " ").replace("Z", "");
			creationTimestamp = elapsedTime(creationTimestamp);
			String status = pvc.getStatus().getPhase();
			String volumeName = pvc.getSpec().getVolumeName();
			String storageClassName = pvc.getSpec().getStorageClassName() == null ? " " : pvc.getSpec().getStorageClassName();
			String capacity = pvc.getStatus().getCapacity().get("storage").getAmount();
			String accessModes = pvc.getStatus().getAccessModes().get(0);
			
			System.out.println(b(namespace, 15) + "  " +b(name, 50) + "  " + b(status, 6) + "  " + b(volumeName, 41)+ "  " + b(capacity, 8)+ "  " + b(accessModes, 14)+ "  " + b(storageClassName, 17)+ "  " + b("Age", 15));
		}
		
	}

	public static void pods() throws Exception {
//Pod(apiVersion=v1, kind=Pod, metadata=ObjectMeta(annotations={kubernetes.io/psp=ibm-privileged-psp}, clusterName=null, creationTimestamp=2018-08-03T03:51:35Z, deletionGracePeriodSeconds=null, deletionTimestamp=null, finalizers=[], generateName=sample-6c466bb97d-, generation=null, initializers=null, labels={app=sample, pod-template-hash=2702266538, version=0.0.1}, name=sample-6c466bb97d-xqggv, namespace=dev02, ownerReferences=[OwnerReference(apiVersion=extensions/v1beta1, blockOwnerDeletion=true, controller=true, kind=ReplicaSet, name=sample-6c466bb97d, uid=734d6be7-93b5-11e8-aa8b-1e84ac9699c4, additionalProperties={})], resourceVersion=4070688, selfLink=/api/v1/namespaces/dev02/pods/sample-6c466bb97d-xqggv, uid=84b5c9b7-96d0-11e8-82f4-1e84ac9699c4, additionalProperties={}), spec=PodSpec(activeDeadlineSeconds=null, affinity=null, automountServiceAccountToken=null, containers=[Container(args=[], command=[], env=[], envFrom=[], image=registry.cloudzcp.io/dev02/sample:latest, imagePullPolicy=Always, lifecycle=null, livenessProbe=null, name=sample, ports=[ContainerPort(containerPort=8080, hostIP=null, hostPort=null, name=null, protocol=TCP, additionalProperties={})], readinessProbe=null, resources=ResourceRequirements(limits=null, requests=null, additionalProperties={}), securityContext=null, stdin=null, stdinOnce=null, terminationMessagePath=/dev/termination-log, terminationMessagePolicy=File, tty=null, volumeMounts=[VolumeMount(mountPath=/var/run/secrets/kubernetes.io/serviceaccount, name=default-token-r6qr4, readOnly=true, subPath=null, additionalProperties={})], workingDir=null, additionalProperties={})], dnsPolicy=ClusterFirst, hostAliases=[], hostIPC=null, hostNetwork=null, hostPID=null, hostname=null, imagePullSecrets=[], initContainers=[], nodeName=10.178.218.188, nodeSelector=null, restartPolicy=Always, schedulerName=default-scheduler, securityContext=PodSecurityContext(fsGroup=null, runAsNonRoot=null, runAsUser=null, seLinuxOptions=null, supplementalGroups=[], additionalProperties={}), serviceAccount=default, serviceAccountName=default, subdomain=null, terminationGracePeriodSeconds=30, tolerations=[Toleration(effect=NoExecute, key=node.kubernetes.io/not-ready, operator=Exists, tolerationSeconds=300, value=null, additionalProperties={}), Toleration(effect=NoExecute, key=node.kubernetes.io/unreachable, operator=Exists, tolerationSeconds=300, value=null, additionalProperties={})], volumes=[Volume(awsElasticBlockStore=null, azureDisk=null, azureFile=null, cephfs=null, cinder=null, configMap=null, downwardAPI=null, emptyDir=null, fc=null, flexVolume=null, flocker=null, gcePersistentDisk=null, gitRepo=null, glusterfs=null, hostPath=null, iscsi=null, name=default-token-r6qr4, nfs=null, persistentVolumeClaim=null, photonPersistentDisk=null, portworxVolume=null, projected=null, quobyte=null, rbd=null, scaleIO=null, secret=SecretVolumeSource(defaultMode=420, items=[], optional=null, secretName=default-token-r6qr4, additionalProperties={}), storageos=null, vsphereVolume=null, additionalProperties={})], additionalProperties={}), status=PodStatus(conditions=[PodCondition(lastProbeTime=null, lastTransitionTime=2018-08-03T03:51:35Z, message=null, reason=null, status=True, type=Initialized, additionalProperties={}), PodCondition(lastProbeTime=null, lastTransitionTime=2018-08-03T03:51:37Z, message=null, reason=null, status=True, type=Ready, additionalProperties={}), PodCondition(lastProbeTime=null, lastTransitionTime=2018-08-03T03:51:35Z, message=null, reason=null, status=True, type=PodScheduled, additionalProperties={})], containerStatuses=[ContainerStatus(containerID=docker://91a2f5f15b818a057c7c7123e703ec6676818484fdad315418530ea3e881d033, image=registry.cloudzcp.io/dev02/sample:latest, imageID=docker-pullable://registry.cloudzcp.io/dev02/sample@sha256:2b41d8cebef0cb183f55d7d63edec871118930f63ffe9a968ca1f5078fc5ca63, lastState=ContainerState(running=null, terminated=null, waiting=null, additionalProperties={}), name=sample, ready=true, restartCount=0, state=ContainerState(running=ContainerStateRunning(startedAt=2018-08-03T03:51:37Z, additionalProperties={}), terminated=null, waiting=null, additionalProperties={}), additionalProperties={})], hostIP=10.178.218.188, initContainerStatuses=[], message=null, phase=Running, podIP=172.30.231.87, qosClass=BestEffort, reason=null, startTime=2018-08-03T03:51:35Z, additionalProperties={}), additionalProperties={})
		List<String> nsList = getNamespacesList();
		System.out.println(cc("=", 170));
		System.out.println("#Pods");
		System.out.println(cc("=", 170));
		System.out.println(b("Namespace", 15) + "  " +b("Name", 50) + "  " + b("Status", 8) + "  " + b("Alive", 8) + "  " + b("isInit", 8)+ "  " + b("isReady", 8)+ "  " + b("isPodScheduled", 16)+ "  " + b("isContainerReady", 16)+ "  " + b("Age", 15));
		System.out.println(cc("=", 170));
		
		List<Pod> items = kubernetesClient().pods().list().getItems();
		
		for (Pod pod : items) {
			String namespace = pod.getMetadata().getNamespace();
			if(!nsList.contains(namespace)) {
				continue;
			}
			
			String name = pod.getMetadata().getName();
			String creationTimestamp = pod.getMetadata().getCreationTimestamp();
			creationTimestamp = creationTimestamp.replace("T", " ").replace("Z", "");
			creationTimestamp = elapsedTime(creationTimestamp);
			String status = pod.getStatus().getPhase();
			
			System.out.println(b(namespace, 15) + "  " +b(name, 50) + "  " + b(status, 8) + "  " + b(podStatus(pod)+"", 8)  + "  " + b(isInitialized(pod)+"", 8) + "  " + b(isReady(pod)+"", 8) + "  " + b(isPodScheduled(pod)+"", 16)  + "  " + b(isContainerReady(pod)+"", 16)+ "  " + getPodLastTransitionTime(pod));
		}
		
	
	}
	
	public static void services() throws Exception {

		List<String> nsList = getNamespacesList();
		List<Service> items = kubernetesClient().services().list().getItems();

		int l[] = new int[] {15,45, 16, 16, 15};
		
		System.out.println(cc("=", 170));
		System.out.println("#Service");
		System.out.println(cc("=", 170));
		System.out.println(b("Namespace", l[0]) + "  " +b("Name", l[1]) + "  " + b("ClusterIP", l[2]) + "  " + b("LB IP", l[3]) + "  " + b("Age", l[4]));
		System.out.println(cc("=", 170));
		
		
		for (Service service : items) {
			String namespace = service.getMetadata().getNamespace();
			if(!nsList.contains(namespace)) {
				continue;
			}
			
			String name = service.getMetadata().getName();
			
			String creationTimestamp = service.getMetadata().getCreationTimestamp();
			creationTimestamp = creationTimestamp.replace("T", " ").replace("Z", "");
			creationTimestamp = elapsedTime(creationTimestamp);
			
			String clusterIP = service.getSpec().getClusterIP();
			
			List<LoadBalancerIngress> ingress = service.getStatus().getLoadBalancer().getIngress();
			
			String loadBalancerIngress = "";
			if(ingress != null && !ingress.isEmpty()) {
				loadBalancerIngress = ingress.get(0).getIp();
			}
			
			System.out.println(b(namespace, l[0]) + "  " +b(name, l[1]) + "  " + b(clusterIP, l[2]) + "  " + b(loadBalancerIngress, l[3]) + "  " + creationTimestamp);
		}
	
		
	}

	public static void configmaps() throws Exception {
		List<String> nsList = getNamespacesList();
		List<ConfigMap> items = kubernetesClient().configMaps().list().getItems();

		System.out.println(cc("=", 170));
		System.out.println("#ConfigMap");
		System.out.println(cc("=", 170));
		System.out.println(b("Namespace", 15) + "  " +b("Name", 40)  + "  " + b("Age", 15));
		System.out.println(cc("=", 170));
		
		
		for (ConfigMap configMap : items) {
			String namespace = configMap.getMetadata().getNamespace();
			if(!nsList.contains(namespace)) {
				continue;
			}
			
			String name = configMap.getMetadata().getName();
			Map<String, String> data = configMap.getData();
			
			String creationTimestamp = configMap.getMetadata().getCreationTimestamp();
			creationTimestamp = creationTimestamp.replace("T", " ").replace("Z", "");
			creationTimestamp = elapsedTime(creationTimestamp);
			
			System.out.println(b(namespace, 15) + "  " +b(name, 40) + "  " + b("", 6) + "  " + creationTimestamp);
		}
	}
	
	public static void secrets() throws Exception {
//Secret(apiVersion=v1, data={redis-password=dWI4Z0Y5YU5ZcA==}, kind=Secret, metadata=ObjectMeta(annotations=null, clusterName=null, creationTimestamp=2018-08-07T00:28:27Z, deletionGracePeriodSeconds=null, deletionTimestamp=null, finalizers=[], generateName=null, generation=null, initializers=null, labels={app=redis, chart=redis-3.6.5, heritage=Tiller, release=ns-zdb-01-ha-pub}, name=ns-zdb-01-ha-pub-redis, namespace=ns-zdb-01, ownerReferences=[], resourceVersion=4319386, selfLink=/api/v1/namespaces/ns-zdb-01/secrets/ns-zdb-01-ha-pub-redis, uid=cd888624-99d8-11e8-82f4-1e84ac9699c4, additionalProperties={}), stringData=null, type=Opaque, additionalProperties={})
		List<String> nsList = getNamespacesList();
		List<Secret> items = kubernetesClient().secrets().list().getItems();

		System.out.println(cc("=", 170));
		System.out.println("#Secret");
		System.out.println(cc("=", 170));
		System.out.println(b("Namespace", 15) + "  " +b("Name", 40)   + "  " +b("password", 25) + "  " + b("Age", 15));
		System.out.println(cc("=", 170));
		
		
		for (Secret secret : items) {
			String namespace = secret.getMetadata().getNamespace();
			if(!nsList.contains(namespace)) {
				continue;
			}
			
			if(!"Opaque".equals(secret.getType())) {
				continue;
			}
			
			String name = secret.getMetadata().getName();
			
			Map<String, String> labels = secret.getMetadata().getLabels();
			String app = labels.get("app");
			
			Map<String, String> data = secret.getData();
			
			String pwd = "";
			if ("mariadb".equals(app)) {
				pwd = data.get("mariadb-password");
			} else if ("redis".equals(app)) {
				pwd = data.get("redis-password");
			}
			
			if(!pwd.isEmpty()) {
				pwd = new String(Base64.getDecoder().decode(pwd));
			}
			
			String creationTimestamp = secret.getMetadata().getCreationTimestamp();
			creationTimestamp = creationTimestamp.replace("T", " ").replace("Z", "");
			creationTimestamp = elapsedTime(creationTimestamp);
			
			System.out.println(b(namespace, 15) + "  " +b(name, 40) + "  " + b(pwd, 25) + "  " + creationTimestamp);
		}
		
	
	}
	public static void ingresses() throws Exception {
		List<String> nsList = getNamespacesList();
		List<Ingress> items = kubernetesClient().extensions().ingresses().list().getItems();
		
		System.out.println(cc("=", 183));
		System.out.println("#Ingress");
		System.out.println(cc("=", 183));
		System.out.println(b("Namespace", 15) + "  " +b("Name", 40)   + "  " +b("Serivce Name", 40) + "  " +b("Serivce Port", 12) + "  " +b("Host", 60) + "  " + b("Age", 15));
		System.out.println(cc("=", 183));
		
		
		for (Ingress ingress : items) {
			String namespace = ingress.getMetadata().getNamespace();
			if(!nsList.contains(namespace)) {
				continue;
			}
			
			String name = ingress.getMetadata().getName();
			String host = "";
			String serviceName = "";
			Integer servicePort = 0;
			
			List<IngressRule> rules = ingress.getSpec().getRules();
			if(rules != null && !rules.isEmpty()) {
				IngressRule ingressRule = rules.get(0);
				host = ingressRule.getHost();
				serviceName = ingressRule.getHttp().getPaths().get(0).getBackend().getServiceName();
				servicePort = ingressRule.getHttp().getPaths().get(0).getBackend().getServicePort().getIntVal();
			}
			
			String creationTimestamp = ingress.getMetadata().getCreationTimestamp();
			creationTimestamp = creationTimestamp.replace("T", " ").replace("Z", "");
			creationTimestamp = elapsedTime(creationTimestamp);
			String n = "";
			if(servicePort == null) {
				n = "";
			} else {
				n = servicePort+"";
			}
			System.out.println(b(namespace, 15) + "  " +b(name, 40) + "  " + b(serviceName, 40) + "  " + b(n, 12) + "  " + b(host, 60) + "  " + creationTimestamp);
		}
		
		
	}
	
	public static void nodes() throws Exception {
		ListIterator<Node> nodelist = kubernetesClient().nodes().list().getItems().listIterator();
		
		System.out.println(cc("=", 170));
		System.out.println("#Nodes");
		System.out.println(cc("=", 170));
		System.out.println(b("Name", 14) + "  " + b("Labels", 11) + " " + b("Ready", 5)+ "  " + b("CPU", 8)+ "  " + b("Memory", 21) + "  " + b("Age", 15));
		System.out.println(cc("=", 170));
		while(nodelist.hasNext()) {
			Node node = nodelist.next();
			
			String name = node.getMetadata().getName();
			String role = node.getMetadata().getLabels().get("role") == null ?  b("", 10) : b(node.getMetadata().getLabels().get("role"), 10);
			
			String phase = node.getStatus().getPhase();
			NodeSystemInfo nodeInfo = node.getStatus().getNodeInfo();
			String kubeProxyVersion = nodeInfo.getKubeProxyVersion();
			String status = "";
			String creationTimestamp = "";
			creationTimestamp = node.getMetadata().getCreationTimestamp();
			creationTimestamp = creationTimestamp.replace("T", " ").replace("Z", "");
			creationTimestamp = elapsedTime(creationTimestamp);
			
			Map<String, Quantity> capacity = node.getStatus().getCapacity();
			Quantity capacityCpu = capacity.get("cpu");
			Quantity capacityMemory = capacity.get("memory");
			
			Map<String, Quantity> allocatable = node.getStatus().getAllocatable();
			Quantity allocatableCpu = allocatable.get("cpu");
			Quantity allocatableMemory = allocatable.get("memory");
			
			List<NodeCondition> conditions = node.getStatus().getConditions();
			for (NodeCondition nodeCondition : conditions) {
				if("Ready".equals(nodeCondition.getType())) {
					status = nodeCondition.getStatus();
					
				}
			}
			
			String amount = allocatableMemory.getAmount();
			long mem = Long.parseLong(amount);
			
			System.out.println(b(name,15) + " "+ b(role, 11)+ " "+ b(status,6) + b(" ("+ capacityCpu.getAmount()+ " | "+ allocatableCpu.getAmount()+ ")",10) + b("("+ capacityMemory.getAmount()+ " | " + mem / 1000/1000 +"M)",24) +"" + creationTimestamp);
		}
	}
	
	static String b(String n, int len) {
		if(n == null) n= "";
		int blank = len - n.length();
		
		for (int i = 0; i < blank; i++) {
			n = n+" ";
		}
		
		return n;
	}
	
	static String cc(String n, int len) {
		if(n == null) n= "";
		for (int i = 0; i < len; i++) {
			n = n+"=";
		}
		
		return n;
	}

	static public String elapsedTime(String dateStr){
		SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
		
		long elapsedDays;
		long elapsedHours;
		long elapsedMinutes;
		long elapsedSeconds;
		try {
			Date endDate = sdf.parse(dateStr);
			long currentTime = System.currentTimeMillis();
			
			//milliseconds
			long different = currentTime - endDate.getTime();
			
			long secondsInMilli = 1000;
			long minutesInMilli = secondsInMilli * 60;
			long hoursInMilli = minutesInMilli * 60;
			long daysInMilli = hoursInMilli * 24;

			elapsedDays = different / daysInMilli;
			different = different % daysInMilli;
			
			elapsedHours = different / hoursInMilli;
			different = different % hoursInMilli;
			
			elapsedMinutes = different / minutesInMilli;
			different = different % minutesInMilli;
			
			elapsedSeconds = different / secondsInMilli;

			if (elapsedDays > 0) {
				return elapsedDays +" "+ "days";
			} else if (elapsedHours > 0) {
				return elapsedHours +" hours";
			} else if (elapsedMinutes > 0) {
				return elapsedMinutes +" min";
			} else if (elapsedSeconds > 0) {
				return elapsedSeconds +" sec";
			} else {
				return "";
			}
		} catch (Exception e) {
			e.printStackTrace();
		}
		
		return "";
	}
	
	static DefaultKubernetesClient client = null;
	
	public static DefaultKubernetesClient kubernetesClient() throws Exception {
		if(client !=null) {
			return client;
		}
		String idToken = null;

		idToken = System.getProperty("token");
		String masterUrl = System.getProperty("masterUrl");

		if (idToken == null || masterUrl == null) {
			System.err.println("VM arguments 설정 후 실행 하세요...\n-DmasterUrl=xxx.xxx.xxx.xx:12345 -Dtoken=xxxxxx");
			System.exit(-1);
		}

		System.setProperty(Config.KUBERNETES_AUTH_TRYSERVICEACCOUNT_SYSTEM_PROPERTY, "false");

		Config config = new ConfigBuilder().withMasterUrl(masterUrl).withOauthToken(idToken).withTrustCerts(true).withWatchReconnectLimit(-1).build();
		client = new DefaultKubernetesClient(config);

		return client;

	}
	
	public static boolean podStatus(Pod pod) {
		boolean isSuccess = false;
		
		try {
			PodStatus status = pod.getStatus();
			String name = pod.getMetadata().getName();
			String phase = status.getPhase();
			
			String reason = status.getReason();
			String message = status.getMessage();
			
			boolean isInitialized = false;
			boolean isReady = false;
			boolean isPodScheduled = false;
			
			List<PodCondition> conditions = status.getConditions();
			for (PodCondition condition : conditions) {
				String podConditionMessage = condition.getMessage();
				String podConditionReason = condition.getReason();
				
				if ("Initialized".equals(condition.getType())) {
					isInitialized = Boolean.parseBoolean(condition.getStatus());
				}
				
				if ("Ready".equals(condition.getType())) {
					isReady = Boolean.parseBoolean(condition.getStatus());
				}
				
				if ("PodScheduled".equals(condition.getType())) {
					isPodScheduled = Boolean.parseBoolean(condition.getStatus());
				}
			}
			
			List<ContainerStatus> containerStatuses = status.getContainerStatuses();
			
			boolean isContainerReady = false;
			for (ContainerStatus containerStatus : containerStatuses) {
				Boolean ready = containerStatus.getReady();
				if (!ready.booleanValue()) {
					isContainerReady = false;
					break;
				} else {
					isContainerReady = true;
				}
			}
			
			if (isInitialized && isReady && isPodScheduled && isContainerReady) {
				isSuccess = true;
			} else {
				//log.info("Name : {}, Initialized : {}, Ready : {}, PodScheduled : {}, isContainerReady : {}, reason : {}, message : {}", name, isInitialized, isReady, isPodScheduled, isContainerReady, reason, message);
			}
		} catch (Exception e) {
			e.printStackTrace();
		}

		return isSuccess;
	}
	
	public static boolean isInitialized(Pod pod) {
		try {
			PodStatus status = pod.getStatus();
			
			List<PodCondition> conditions = status.getConditions();
			for (PodCondition condition : conditions) {
				if ("Initialized".equals(condition.getType())) {
					return Boolean.parseBoolean(condition.getStatus());
				}
			}
		} catch (Exception e) {
			e.printStackTrace();
		}
		
		return false;
	}
	
	public static boolean isReady(Pod pod) {
		try {
			PodStatus status = pod.getStatus();
			
			List<PodCondition> conditions = status.getConditions();
			for (PodCondition condition : conditions) {
				if ("Ready".equals(condition.getType())) {
					return Boolean.parseBoolean(condition.getStatus());
				}
			}
			
		} catch (Exception e) {
			e.printStackTrace();
		}
		
		return false;
	}
	
	public static String getPodLastTransitionTime(Pod pod) {
		try {
			PodStatus status = pod.getStatus();
			
			List<PodCondition> conditions = status.getConditions();
			for (PodCondition condition : conditions) {
				if ("Ready".equals(condition.getType())) {
					
					String lastTransitionTime = condition.getLastTransitionTime();
					lastTransitionTime = lastTransitionTime.replace("T", " ").replace("Z", "");
					lastTransitionTime = elapsedTime(lastTransitionTime);
					
					return lastTransitionTime;
				}
			}
			
		} catch (Exception e) {
			e.printStackTrace();
		}
		
		return "";
	}
	
	public static boolean isPodScheduled(Pod pod) {
		try {
			PodStatus status = pod.getStatus();
			
			List<PodCondition> conditions = status.getConditions();
			for (PodCondition condition : conditions) {
				if ("PodScheduled".equals(condition.getType())) {
					return Boolean.parseBoolean(condition.getStatus());
				}
			}
			
		} catch (Exception e) {
			e.printStackTrace();
		}
		
		return false;
	}
	
	public static boolean isContainerReady(Pod pod) {
		boolean isContainerReady = false;
		try {
			PodStatus status = pod.getStatus();
			
			List<ContainerStatus> containerStatuses = status.getContainerStatuses();
			
			for (ContainerStatus containerStatus : containerStatuses) {
				Boolean ready = containerStatus.getReady();
				if (!ready.booleanValue()) {
					isContainerReady = false;
					break;
				} else {
					isContainerReady = true;
				}
			}
		} catch (Exception e) {
			e.printStackTrace();
		}
		
		return isContainerReady;
	}
}
