package com.zdb.snippet;

import java.util.concurrent.CountDownLatch;

import com.zdb.core.job.CreatePersistentVolumeClaimsJob;
import com.zdb.core.job.DataCopyJob;
import com.zdb.core.job.EventAdapter;
import com.zdb.core.job.EventListener;
import com.zdb.core.job.Job;
import com.zdb.core.job.JobExecutor;
import com.zdb.core.job.JobHandler;
import com.zdb.core.job.JobParameter;
import com.zdb.core.job.ShutdownServiceJob;
import com.zdb.core.job.StartServiceJob;
import com.zdb.core.job.Job.JobResult;

import lombok.extern.slf4j.Slf4j;

@Slf4j
public class StorageScaleTest {

	public static void main(String[] args) {
		String accessMode = "ReadWriteOnce";
		String billingType = "hourly";
		String storageClass = "ibmc-block-silver";

		String namespace = "zdb-test2";
		String podName = "zdb-test2-ns-mariadb-0";
		String serviceType = "mariadb";
		String serviceName = "zdb-test2-ns";
		String size = "40Gi";
		String statefulsetName = "zdb-test2-ns-mariadb";

		JobParameter param = new JobParameter();
		param.setNamespace(namespace);
		param.setPodName(podName);
		param.setServiceType(serviceType);
		param.setServiceName(serviceName);
		param.setAccessMode(accessMode == null ? "ReadWriteOnce" : accessMode);
		param.setBillingType(billingType == null ? "hourly" : billingType);
		param.setSize(size);
		param.setStorageClass(storageClass == null ? "ibmc-block-silver" : storageClass);
		param.setStatefulsetName(statefulsetName);

		CreatePersistentVolumeClaimsJob job1 = new CreatePersistentVolumeClaimsJob(param);
		ShutdownServiceJob              job2 = new ShutdownServiceJob(param);
		DataCopyJob                     job3 = new DataCopyJob(param);
		StartServiceJob                 job4 = new StartServiceJob(param);

		Job[] jobs = new Job[] { job1, job2, job3, job4 };

		CountDownLatch latch = new CountDownLatch(jobs.length);

		JobExecutor storageScaleExecutor = new JobExecutor(latch,"txid");

		EventListener eventListener = new EventAdapter("txid") {

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

		JobHandler.getInstance().addListener(eventListener);

		storageScaleExecutor.execTask(jobs);

		try {
			latch.await();
			JobHandler.getInstance().removeListener(eventListener);
		} catch (InterruptedException e) {
			log.error(e.getMessage(), e);
		}
	}

}
