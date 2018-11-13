package com.zdb.snippet;

import java.util.concurrent.CountDownLatch;

import com.zdb.core.job.JobAdapter;
import com.zdb.core.job.JobKind;
import com.zdb.core.job.JobParameter;
import com.zdb.core.job.Job.JobResult;

import lombok.extern.slf4j.Slf4j;

@Slf4j
public class EmptyJob extends JobAdapter {

	public EmptyJob(JobParameter param) {
		super(param);
	}

	@Override
	public String getJobName() {
		return "1-Test service";
	}

	@Override
	public JobKind getKind() {
		return JobKind.START_POD;
	}

	@Override
	public void run() {
		String stsName = param.getStatefulsetName();
		
		try{
			
			final CountDownLatch latch = new CountDownLatch(20);
			
			while(latch.getCount() > 0) {
				
				Thread.sleep(900);
				progress(getJobName()  + " / " + stsName+ " / " + latch.getCount()  );
				
				latch.countDown();
			}
			
			latch.await();
			
			done(JobResult.OK, getJobName() +" done.", null);
			
		} catch (Exception e) {
			done(JobResult.ERROR, stsName + " done.", e);
		}
		
	}

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
		param.setAccessMode(accessMode);
		param.setBillingType(billingType);
		param.setSize(size);
		param.setStorageClass(storageClass);
		param.setStatefulsetName(statefulsetName);
		param.setTargetPvc("data-zdb-test2-ns-mariadb-1");
		//"zdb-test2", "zdb-test2-ns-mariadb", "data-zdb-test2-ns-mariadb-1"
		
		EmptyJob job = new EmptyJob(param);
		job.run();
	}

}
