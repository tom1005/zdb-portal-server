package com.zdb.snippet;

import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import com.zdb.snippet.Job.JobResult;

import lombok.extern.slf4j.Slf4j;

@Slf4j
public class JobExecutor {
	
	static BlockingQueue<Job> taskQueue = null;
	
	ExecutorService executorService = null;
	
	CountDownLatch countDownLatch = null;
	
	public JobExecutor(CountDownLatch latch) {
		this.countDownLatch = latch;

		if (taskQueue == null) {
			taskQueue = new ArrayBlockingQueue<Job>(100);
		}
	}
	
	private void shutdown() {
		JobHandler.removeListener(eventListener);
		
		if(executorService != null && !executorService.isTerminated()) {
			executorService.shutdownNow();
			System.out.println("shutdownNow.");
		}
	}
	
	public void putTask(Job[] jobs) {
		setContinue(true);
		
		if(taskQueue != null) {
			try {
				for (Job job : jobs) {
					taskQueue.put(job);
				}
				exec();
			} catch (InterruptedException e) {
				e.printStackTrace();
			}
		}
	}
	
	private void exec() {
		if( executorService != null) {
//			executorService.shutdownNow();
//			executorService = null;
		}
		
		JobHandler.addListener(eventListener);
		
		executorService = Executors.newSingleThreadExecutor();
		executorService.execute(new DeploymentConsumer("worker - " , taskQueue));
	}
	
	boolean isContinue = true;
	
	public void setContinue(boolean bool) {
		isContinue = bool;
	}
	
	public boolean isContinue() {
		return isContinue;
	}
	
	EventListener eventListener = new EventAdapter() {

		@Override
		public void done(Job job, JobResult code, Throwable e) {
			setContinue(code == JobResult.OK ? true : false);
		}

	};

	private class DeploymentConsumer implements Runnable {
		private BlockingQueue<Job> queue;
		String workerId;

		public DeploymentConsumer(String id, BlockingQueue<Job> queue) {
			this.queue = queue;
			this.workerId = id;
		}

		@Override
		public void run() {
			Job prevJob = null;
			int step = 1;
			long stepTotalCount = countDownLatch.getCount();
			
			while (countDownLatch.getCount() > 0) {
				try {
					Thread.sleep(100);
					Job job = (Job) queue.take();
					
					log.info("Step[" + (step++) + "/"+ stepTotalCount +"] : "+job.getJobName() +" start.");
					if(isContinue()) {
						job.run();
					} else {
						shutdown();
						break;
					}
					prevJob = job;

					countDownLatch.countDown();
				} catch (Exception e) {
					log.error(e.getMessage(), e);
				}
				
			}
			shutdown();
		}
	}
}
