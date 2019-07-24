package com.zdb.core;

import java.util.List;
import java.util.Map;

import com.zdb.core.util.K8SUtil;

import io.fabric8.kubernetes.api.model.PersistentVolumeClaim;
import io.fabric8.kubernetes.api.model.Quantity;
import io.fabric8.kubernetes.client.DefaultKubernetesClient;

public class PVCTest {
	
	
	public static void main(String[] args) throws Exception {
		DefaultKubernetesClient client = K8SUtil.kubernetesClient();
		
		List<PersistentVolumeClaim> items = client.inAnyNamespace().persistentVolumeClaims().list().getItems();
		for (PersistentVolumeClaim pvcItem : items) {

			String scn = pvcItem.getSpec().getStorageClassName();
			
			if(scn == null) {
				try {
					scn = pvcItem.getMetadata().getAnnotations().get("volume.beta.kubernetes.io/storage-class");
				} catch (Exception e) {
					continue;
				}
			}
			
			String iops = "-";
			String storage = "-";
			Map<String, Quantity> requests = pvcItem.getSpec().getResources().getRequests();
			Quantity iopsQuantity = requests.get("iops");
			Quantity storageQuantity = requests.get("storage");

			if (iopsQuantity != null) {
				iops = iopsQuantity.getAmount();
			}
			if (storageQuantity != null) {
				storage = storageQuantity.getAmount();
			}

			if (scn.indexOf("bronze") > -1) {
				int iopsValue = 2;
				if (storage.endsWith("Gi")) {
					String temp = storage.replaceAll("Gi", "");
					iops = String.valueOf(Integer.parseInt(temp) * iopsValue);
				} else if (storage.endsWith("Ti")) {
					String temp = storage.replaceAll("Ti", "");
					iops = String.valueOf(Integer.parseInt(temp) * 1000 * iopsValue);
				}
			} else if (scn.indexOf("silver") > -1) {
				int iopsValue = 4;
				if (storage.endsWith("Gi")) {
					String temp = storage.replaceAll("Gi", "");
					iops = String.valueOf(Integer.parseInt(temp) * iopsValue);
				} else if (storage.endsWith("Ti")) {
					String temp = storage.replaceAll("Ti", "");
					iops = String.valueOf(Integer.parseInt(temp) * 1000 * iopsValue);
				}
			} else if (scn.indexOf("gold") > -1) {
				int iopsValue = 10;
				if (storage.endsWith("Gi")) {
					String temp = storage.replaceAll("Gi", "");
					iops = String.valueOf(Integer.parseInt(temp) * iopsValue);
				} else if (storage.endsWith("Ti")) {
					String temp = storage.replaceAll("Ti", "");
					iops = String.valueOf(Integer.parseInt(temp) * 1000 * iopsValue);
				}
			} else if (scn.indexOf("custom") > -1) {

			}
			System.out.println(pvcItem.getMetadata().getName()+" \t/\t "+scn+" \t/\t "+iops+" \t/\t "+storage);
		}
	}

}
