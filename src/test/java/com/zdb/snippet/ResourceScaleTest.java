package com.zdb.snippet;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import com.zdb.core.job.EventListener;
import com.zdb.core.job.Job;
import com.zdb.core.job.Job.JobResult;
import com.zdb.core.job.JobExecutor;
import com.zdb.core.job.JobHandler;
import com.zdb.core.job.JobParameter;
import com.zdb.core.job.ResourceScaleJob;

import lombok.extern.slf4j.Slf4j;

@Slf4j
public class ResourceScaleTest {

	public static void main(String[] args) {
//		String accessMode = "ReadWriteOnce";
//		String billingType = "hourly";
//		String storageClass = "ibmc-block-silver";

		String namespace = "zdb-test";
		String serviceName = "zdb-test-ns9";
		String serviceType = "mariadb";
//		String podName = "zdb-test2-ns-mariadb-0";
//		String size = "40Gi";
//		String statefulsetName = "zdb-test2-ns-mariadb";
		
		ExecutorService executorService1 = Executors.newSingleThreadExecutor();
		
		ExecutorService executorService2 = Executors.newSingleThreadExecutor();
		
		System.out.println();

		JobParameter param = new JobParameter();
		param.setNamespace(namespace);
		param.setServiceType(serviceType);
		param.setServiceName(serviceName);
//		param.setPodName(podName);
//		param.setAccessMode(accessMode == null ? "ReadWriteOnce" : accessMode);
//		param.setBillingType(billingType == null ? "hourly" : billingType);
//		param.setSize(size);
//		param.setStorageClass(storageClass == null ? "ibmc-block-silver" : storageClass);
//		param.setStatefulsetName(statefulsetName);

		ResourceScaleJob job1 = new ResourceScaleJob(param);

		Job[] jobs = new Job[] { job1 };

		CountDownLatch latch = new CountDownLatch(jobs.length);

		JobExecutor storageScaleExecutor = new JobExecutor(latch);

		EventListener eventListener = new EventListener() {

			@Override
			public void onEvent(Job job, String event) {
				//log.info(job.getJobName() + "  onEvent - " + event);
			}

			@Override
			public void status(Job job, String status) {
				//log.info(job.getJobName() + " : " + status);
			}

			@Override
			public void done(Job job, JobResult code, String message, Throwable e) {
				//log.info(job.getJobName() + " complete. [" + code + "]");
			}

		};

		JobHandler.addListener(eventListener);

		storageScaleExecutor.execTask(jobs);

		try {
//			latch.await();
//			JobHandler.removeListener(eventListener);
		} catch (Exception e) {
			log.error(e.getMessage(), e);
		}
	}

}
