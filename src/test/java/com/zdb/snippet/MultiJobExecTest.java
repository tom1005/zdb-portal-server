package com.zdb.snippet;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;

import com.zdb.core.job.EventAdapter;
import com.zdb.core.job.Job;
import com.zdb.core.job.Job.JobResult;
import com.zdb.core.job.JobExecutor;
import com.zdb.core.job.JobHandler;
import com.zdb.core.job.JobParameter;
import com.zdb.core.job.RecoveryStatefulsetJob;

import lombok.extern.slf4j.Slf4j;

@Slf4j
public class MultiJobExecTest {

	public static void main(String[] args) {
		
		CountDownLatch latch = new CountDownLatch(1);
		
		String txId = "xxxxxxxxxxxx";
		JobExecutor ex = new JobExecutor(latch, txId);
		
	}
	
	
	public static void main2(String[] args) {

		JobParameter param = new JobParameter();
		param.setNamespace("zdb-test2");
		param.setStatefulsetName("zdb-test2-qq-mariadb-slave");
		param.setSourcePvc("data-zdb-test2-qq-mariadb-slave-2");
		
		RecoveryStatefulsetJob job = new RecoveryStatefulsetJob(param);
		
		new Thread(job, "Recovery Statefulset Job").start();
		
//		{
//			JobParameter param = new JobParameter();
//			param.setStatefulsetName("1----------");
//			EmptyJob job = new EmptyJob(param);
//			
//			JobParameter param2 = new JobParameter();
//			param2.setStatefulsetName("11----------");
//			EmptyJob job2 = new EmptyJob(param2);
//			
//			aaa(job, job2, job);
//			System.out.println("--------------- run 1 ---------------");
//		}
//		try {
//			Thread.sleep(2000);
//		} catch (InterruptedException e) {
//		}
//		{
//			JobParameter param = new JobParameter();
//			param.setStatefulsetName("2----------");
//			EmptyJob2 job = new EmptyJob2(param);
//			
//			JobParameter param2 = new JobParameter();
//			param2.setStatefulsetName("22----------");
//			EmptyJob job2 = new EmptyJob(param2);
//			
//			JobParameter param3 = new JobParameter();
//			param3.setStatefulsetName("222----------");
//			EmptyJob job3 = new EmptyJob(param3);
//			
//			aaa(job, job2, job3);
//			
//			System.out.println("--------------- run 2 ---------------");
//		}
	}

	private static void aaa(Job emptyJob, Job emptyJob2, Job emptyJob3) {

		List<Job> jobs = new ArrayList<>();
		jobs.add(emptyJob);
		jobs.add(emptyJob2);
		jobs.add(emptyJob3);

		CountDownLatch latch = new CountDownLatch(jobs.size());

		JobExecutor storageScaleExecutor = new JobExecutor(latch,"");

		EventAdapter eventListener = new EventAdapter("") {

			@Override
			public void onEvent(Job job, String event) {
				//log.info(job.getJobName() + "  onEvent - " + event);
			}

			@Override
			public void status(Job job, String status) {
//				log.info(job.getJobName() + " : " + status);
				
				System.out.println(jobs.indexOf(job) + " / " + job.getJobName());
				
			}

			@Override
			public void done(Job job, JobResult code, String message, Throwable e) {
				System.out.println(job.getJobParameter().getStatefulsetName() + "job.getClass().getName() : "+job.getClass().getName());
				System.out.println(job.getJobParameter().getStatefulsetName() + "EmptyJob2.class.getName() : "+EmptyJob2.class.getName());
				System.out.println(job.getJobParameter().getStatefulsetName() + "EmptyJob.class.getName() : "+EmptyJob.class.getName());
				System.out.println(job.getJobParameter().getStatefulsetName() + "emptyJob.getClass().getName() : "+emptyJob.getClass().getName());
				System.out.println(job.getJobParameter().getStatefulsetName() + "job == emptyJob : " + (job == emptyJob));
				
				
				log.info(job.getJobParameter().getStatefulsetName() +  " / "+job.getJobName() + " complete. [" + code + "]");
			}

		};

		JobHandler.addListener(eventListener);

		storageScaleExecutor.execTask(jobs.toArray(new Job[] {}));

		try {
//			latch.await();
//			JobHandler.removeListener(eventListener);
		} catch (Exception e) {
			log.error(e.getMessage(), e);
		}
	}

}
