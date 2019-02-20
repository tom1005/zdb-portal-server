package com.zdb.core;

import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.zdb.core.util.K8SUtil;

import io.fabric8.kubernetes.api.model.extensions.DoneableStatefulSet;
import io.fabric8.kubernetes.api.model.extensions.StatefulSet;
import io.fabric8.kubernetes.api.model.extensions.StatefulSetBuilder;
import io.fabric8.kubernetes.api.model.extensions.StatefulSetList;
import io.fabric8.kubernetes.client.dsl.MixedOperation;
import io.fabric8.kubernetes.client.dsl.RollableScalableResource;

public class LabelUpdateExample {

	private static final Logger logger = LoggerFactory.getLogger(LabelUpdateExample.class);

	public static void main(String[] args) {
		try {
			MixedOperation<StatefulSet, StatefulSetList, DoneableStatefulSet, RollableScalableResource<StatefulSet, DoneableStatefulSet>> statefulSets = K8SUtil.kubernetesClient().inNamespace("zdb-test2").apps()
					.statefulSets();
//			StatefulSet sts = statefulSets.withName("zdb-test2-mha-mariadb-master").get();

//			Map<String, String> labels = sts.getMetadata().getLabels();
//			labels.put("zdb-failover-enable", "false");
//
//			StatefulSetBuilder newSts = new StatefulSetBuilder(sts);
//			StatefulSet newSvc = newSts.editMetadata().withLabels(labels).endMetadata().build();
//
//			statefulSets.createOrReplace(newSvc);
			
			statefulSets.withName("zdb-test2-mha-mariadb-master").edit().editMetadata().addToLabels("zdb-failover-enable", "true").endMetadata().done();
			

		} catch (Exception e) {
			// TODO: handle exception
			e.printStackTrace();
		}
	}
}