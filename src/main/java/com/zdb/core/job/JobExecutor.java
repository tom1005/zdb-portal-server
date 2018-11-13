package com.zdb.core.job;

import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import com.zdb.core.job.Job.JobResult;

import lombok.extern.slf4j.Slf4j;

@Slf4j
public class JobExecutor {
	
	static BlockingQueue<Job> taskQueue = null;
	
	ExecutorService executorService = null;
	
	CountDownLatch countDownLatch = null;
	
	public JobExecutor(CountDownLatch latch) {
		this.countDownLatch = latch;

		if (taskQueue == null) {
			taskQueue = new ArrayBlockingQueue<Job>(20);
		}
	}
	
	private void shutdown() {
		JobHandler.removeListener(eventListener);
		
		if(executorService != null && !executorService.isTerminated()) {
			executorService.shutdownNow();
			System.out.println("shutdownNow.");
		}
	}
	
	public boolean isShutdown() {
		return executorService.isShutdown();
	}
	
	public synchronized void execTask(Job[] jobs) {
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
	
	private synchronized void exec() {
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
		public void done(Job job, JobResult code, String message, Throwable e) {
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
			
			while (taskQueue.size() > 0) {
				try {
					Thread.sleep(100);
					Job job = (Job) queue.take();
					
					log.info("Step[" + (step++) + "/"+ stepTotalCount +"] : "+job.getJobName());
					if(isContinue()) {
						job.run();
					} else {
						taskQueue.clear();
						shutdown();
						break;
					}
					prevJob = job;
					
					if(job.getStatus() == JobResult.ERROR) {
						taskQueue.clear();
						shutdown();
						break;
					}

					countDownLatch.countDown();
				} catch (Exception e) {
					log.error(e.getMessage(), e);
				}
				
			}
			shutdown();
		}
	}
}
