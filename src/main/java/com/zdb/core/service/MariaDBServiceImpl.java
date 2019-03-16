package com.zdb.core.service;


import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Future;

import org.apache.commons.io.IOUtils;
import org.microbean.helm.ReleaseManager;
import org.microbean.helm.Tiller;
import org.microbean.helm.chart.URLChartLoader;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.ClassPathResource;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.messaging.MessagingException;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.web.client.RestTemplate;

import com.google.gson.Gson;
import com.zdb.core.collector.MetaDataCollector;
import com.zdb.core.domain.Connection;
import com.zdb.core.domain.ConnectionInfo;
import com.zdb.core.domain.DBUser;
import com.zdb.core.domain.IResult;
import com.zdb.core.domain.Mycnf;
import com.zdb.core.domain.PodSpec;
import com.zdb.core.domain.ReleaseMetaData;
import com.zdb.core.domain.RequestEvent;
import com.zdb.core.domain.ResourceSpec;
import com.zdb.core.domain.Result;
import com.zdb.core.domain.ServiceOverview;
import com.zdb.core.domain.ZDBEntity;
import com.zdb.core.domain.ZDBMariaDBAccount;
import com.zdb.core.domain.ZDBMariaDBConfig;
import com.zdb.core.domain.ZDBType;
import com.zdb.core.job.CreatePersistentVolumeClaimsJob;
import com.zdb.core.job.DataCopyJob;
import com.zdb.core.job.EnableAutofailover;
import com.zdb.core.job.EventListener;
import com.zdb.core.job.Job;
import com.zdb.core.job.Job.JobResult;
import com.zdb.core.job.JobExecutor;
import com.zdb.core.job.JobHandler;
import com.zdb.core.job.JobParameter;
import com.zdb.core.job.RecoveryStatefulsetJob;
import com.zdb.core.job.ResourceScaleJob;
import com.zdb.core.job.ServiceOnOffJob;
import com.zdb.core.job.ShutdownServiceJob;
import com.zdb.core.job.StartServiceJob;
import com.zdb.core.repository.DiskUsageRepository;
import com.zdb.core.repository.MycnfRepository;
import com.zdb.core.repository.ZDBMariaDBAccountRepository;
import com.zdb.core.repository.ZDBMariaDBConfigRepository;
import com.zdb.core.repository.ZDBReleaseRepository;
import com.zdb.core.repository.ZDBRepositoryUtil;
import com.zdb.core.util.DateUtil;
import com.zdb.core.util.ExecCommandUtil;
import com.zdb.core.util.K8SUtil;
import com.zdb.core.util.PodManager;
import com.zdb.core.util.ZDBLogViewer;
import com.zdb.mariadb.MariaDBAccount;
import com.zdb.mariadb.MariaDBShutDownUtil;

import hapi.chart.ChartOuterClass.Chart;
import hapi.release.ReleaseOuterClass.Release;
import hapi.services.tiller.Tiller.UpdateReleaseRequest;
import hapi.services.tiller.Tiller.UpdateReleaseResponse;
import io.fabric8.kubernetes.api.model.ConfigMap;
import io.fabric8.kubernetes.api.model.ConfigMapBuilder;
import io.fabric8.kubernetes.api.model.ConfigMapList;
import io.fabric8.kubernetes.api.model.ConfigMapVolumeSource;
import io.fabric8.kubernetes.api.model.Container;
import io.fabric8.kubernetes.api.model.DoneableConfigMap;
import io.fabric8.kubernetes.api.model.DoneablePod;
import io.fabric8.kubernetes.api.model.DoneableService;
import io.fabric8.kubernetes.api.model.LoadBalancerIngress;
import io.fabric8.kubernetes.api.model.PersistentVolumeClaim;
import io.fabric8.kubernetes.api.model.Pod;
import io.fabric8.kubernetes.api.model.Quantity;
import io.fabric8.kubernetes.api.model.Secret;
import io.fabric8.kubernetes.api.model.Service;
import io.fabric8.kubernetes.api.model.ServiceList;
import io.fabric8.kubernetes.api.model.ServicePort;
import io.fabric8.kubernetes.api.model.Volume;
import io.fabric8.kubernetes.api.model.VolumeMount;
import io.fabric8.kubernetes.api.model.extensions.Deployment;
import io.fabric8.kubernetes.api.model.extensions.DoneableStatefulSet;
import io.fabric8.kubernetes.api.model.extensions.StatefulSet;
import io.fabric8.kubernetes.api.model.extensions.StatefulSetBuilder;
import io.fabric8.kubernetes.api.model.extensions.StatefulSetList;
import io.fabric8.kubernetes.client.DefaultKubernetesClient;
import io.fabric8.kubernetes.client.KubernetesClientException;
import io.fabric8.kubernetes.client.dsl.MixedOperation;
import io.fabric8.kubernetes.client.dsl.PodResource;
import io.fabric8.kubernetes.client.dsl.Resource;
import io.fabric8.kubernetes.client.dsl.RollableScalableResource;
import lombok.extern.slf4j.Slf4j;

/**
 * ZDBRestService Implementation
 * 
 * @author 06919
 *
 */
@org.springframework.stereotype.Service("mariadbService")
@Slf4j
@Configuration
public class MariaDBServiceImpl extends AbstractServiceImpl {

	@Value("${chart.mariadb.url}")
	public void setChartUrl(String url) {
		chartUrl = url;
	}
	
	@Autowired
	private ZDBMariaDBAccountRepository zdbMariaDBAccountRepository;
	
	@Autowired
	private ZDBMariaDBConfigRepository zdbMariaDBConfigRepository;

	@Autowired
	private ZDBReleaseRepository releaseRepository;

	@Autowired
	private DiskUsageRepository diskUsageRepository;
	
	@Autowired
	private MycnfRepository configRepository;
	
	@Autowired
	private SimpMessagingTemplate messageSender;

	@Override
	public Result getDeployment(String namespace, String serviceName) {

		try {
			DefaultKubernetesClient client = K8SUtil.kubernetesClient();
			if (client != null) {
				final URI uri = URI.create(chartUrl);
				final URL url = uri.toURL();
				Chart.Builder chart = null;
				try (final URLChartLoader chartLoader = new URLChartLoader()) {
					chart = chartLoader.load(url);
				}

				String chartName = chart.getMetadata().getName();
				String deploymentName = serviceName + "-" + chartName;

				log.debug("deploymentName: {}", deploymentName);
				Deployment deployment = client.inNamespace(namespace).extensions().deployments().withName(deploymentName).get();

				if (deployment != null) {
					return new Result("", Result.OK).putValue(IResult.DEPLOYMENT, deployment);
				} else {
					log.debug("no deployment. namespace: {}, releaseName: {}", namespace, serviceName);
				}
			}
		} catch (FileNotFoundException | KubernetesClientException e) {
			log.error(e.getMessage(), e);
			if (e.getMessage().indexOf("Unauthorized") > -1) {
				return new Result("", Result.UNAUTHORIZED, "클러스터에 접근이 불가하거나 인증에 실패 했습니다.", null);
			} else {
				return new Result("", Result.UNAUTHORIZED, e.getMessage(), e);
			}
		} catch (Exception e) {
			log.error(e.getMessage(), e);
			return new Result("", Result.ERROR, e.getMessage(), e);
		}

		return new Result("", Result.OK).putValue("deployment", "");
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see com.zdb.core.service.ZDBRestService#deploymentList()
	 */
	@Override
	public Result getDeployments(String namespace, String serviceType) throws Exception {
		return super.getDeployments(namespace, serviceType);
	}
		
	@Override
	public Result updateScale(String txId, final ZDBEntity service) throws Exception {
		Result result = new Result(txId);

		String historyValue = "";
		
		try {
			// 서비스 명 체크
			ReleaseMetaData releaseMetaData = releaseRepository.findByReleaseName(service.getServiceName());
			if( releaseMetaData == null) {
				String msg = "서비스가 존재하지 않습니다.";
				return new Result(txId, IResult.ERROR, msg);
			}
			
			PodSpec[] podSpec = service.getPodSpec();
			
			ResourceSpec masterSpec = podSpec[0].getResourceSpec()[0];
			String masterCpu = masterSpec.getCpu();
			String masterMemory = masterSpec.getMemory();
			
			ResourceSpec slaveSpec = podSpec[1].getResourceSpec()[0];
			String slaveCpu = slaveSpec.getCpu();
			String slaveMemory = slaveSpec.getMemory();
			
			// 가용 리소스 체크
			// 현재보다 작으면ok
			// 현재보다 크면 커진 사이즈 만큼 가용량 체크 
			boolean availableResource = isAvailableScaleUp(service);
			
			if(!availableResource) {
				return new Result(txId, IResult.ERROR, "가용 리소스가 부족합니다.");
			}

			// 2018-10-04 추가
			// 환경설정 변경 이력 
			historyValue = compareResources(service.getNamespace(), service.getServiceName(), service);
			
			log.info(service.getServiceName() + " update start.");

			JobParameter param = new JobParameter();
			param.setNamespace(service.getNamespace());
			param.setServiceType(service.getServiceType());
			param.setServiceName(service.getServiceName());
			param.setCpu(masterCpu+"m");
			param.setMemory(masterMemory+"Mi");

			ResourceScaleJob job1 = new ResourceScaleJob(param);

			Job[] jobs = new Job[] { job1 };

			CountDownLatch latch = new CountDownLatch(jobs.length);

			JobExecutor storageScaleExecutor = new JobExecutor(latch);
			
			final String _historyValue = historyValue;
			
			EventListener eventListener = new EventListener() {

				@Override
				public void onEvent(Job job, String event) {
					log.info(job.getJobName() + "  onEvent - " + event);
				}

				@Override
				public void status(Job job, String status) {
					log.info(job.getJobName() + " : " + status);
				}

				@Override
				public void done(Job job, JobResult code, String message, Throwable e) {
					RequestEvent event = new RequestEvent();
					
					event.setTxId(txId);
					event.setStartTime(new Date(System.currentTimeMillis()));
					event.setServiceType(service.getServiceType());
					event.setNamespace(service.getNamespace());
					event.setServiceName(service.getServiceName());
					event.setOperation(RequestEvent.SCALE_UP);
					event.setUserId("SYSTEM");	
					if(code == JobResult.ERROR) {
						event.setStatus(IResult.ERROR);
						event.setResultMessage(job.getJobName() + " 처리 중 오류가 발생했습니다. (" + e.getMessage() +")");
					} else {
						event.setStatus(IResult.OK);
						if(message.isEmpty()) {
							event.setResultMessage(job.getJobName() + " 정상 처리되었습니다.");
						} else {
							event.setResultMessage(message);
						}
					}
					event.setHistory(_historyValue);
					event.setEndTime(new Date(System.currentTimeMillis()));
					ZDBRepositoryUtil.saveRequestEvent(zdbRepository, event);
				}

			};
			JobHandler.addListener(eventListener);

			storageScaleExecutor.execTask(jobs);
			log.info(service.getServiceName() + " update success!");
			result = new Result(txId, IResult.RUNNING, historyValue);
		} catch (FileNotFoundException | KubernetesClientException e) {
			log.error(e.getMessage(), e);

			if (e.getMessage().indexOf("Unauthorized") > -1) {
				return new Result(txId, Result.UNAUTHORIZED, "클러스터에 접근이 불가하거나 인증에 실패 했습니다.", null);
			} else {
				return new Result(txId, Result.UNAUTHORIZED, e.getMessage(), e);
			}
		} catch (Exception e) {
			log.error(e.getMessage(), e);
			return Result.RESULT_FAIL(txId, e);
		} finally {
		}
		
		return result;
	}
	
	public Result updateScale_bak(String txId, final ZDBEntity service) throws Exception {
		Result result = new Result(txId);
		
		ReleaseManager releaseManager = null;
		String historyValue = "";
		
		try {
			final URI uri = URI.create(chartUrl);
			final URL url = uri.toURL();
			Chart.Builder chart = null;
			try (final URLChartLoader chartLoader = new URLChartLoader()) {
				chart = chartLoader.load(url);
			}
			
			// 서비스 명 체크
			ReleaseMetaData releaseMetaData = releaseRepository.findByReleaseName(service.getServiceName());
			if( releaseMetaData == null) {
				String msg = "서비스가 존재하지 않습니다.";
				return new Result(txId, IResult.ERROR, msg);
			}
			
			PodSpec[] podSpec = service.getPodSpec();
			
			ResourceSpec masterSpec = podSpec[0].getResourceSpec()[0];
			String masterCpu = masterSpec.getCpu();
			String masterMemory = masterSpec.getMemory();
			
			ResourceSpec slaveSpec = podSpec[1].getResourceSpec()[0];
			String slaveCpu = slaveSpec.getCpu();
			String slaveMemory = slaveSpec.getMemory();
			
			// 가용 리소스 체크
			// 현재보다 작으면ok
			// 현재보다 크면 커진 사이즈 만큼 가용량 체크 
			boolean availableResource = isAvailableScaleUp(service);
			
			if(!availableResource) {
				return new Result(txId, IResult.ERROR, "가용 리소스가 부족합니다.");
			}
			
			DefaultKubernetesClient client = K8SUtil.kubernetesClient();
			
			final Tiller tiller = new Tiller(client);
			releaseManager = new ReleaseManager(tiller);
			
			final UpdateReleaseRequest.Builder requestBuilder = UpdateReleaseRequest.newBuilder();
			requestBuilder.setTimeout(300L);
			requestBuilder.setName(service.getServiceName());
			requestBuilder.setWait(false);
			
			requestBuilder.setReuseValues(true);
			
			hapi.chart.ConfigOuterClass.Config.Builder valuesBuilder = requestBuilder.getValuesBuilder();
			
			InputStream is = new ClassPathResource("mariadb/update_values.template").getInputStream();
			
			String inputJson = IOUtils.toString(is, StandardCharsets.UTF_8.name());
			
			// 2018-10-04 추가
			// 환경설정 변경 이력 
			historyValue = compareResources(service.getNamespace(), service.getServiceName(), service);
			
			inputJson = inputJson.replace("${master.resources.requests.cpu}", masterCpu);// input , *******   필수값  
			inputJson = inputJson.replace("${master.resources.requests.memory}", masterMemory);// input *******   필수값 
			inputJson = inputJson.replace("${slave.resources.requests.cpu}", slaveCpu);// input*******   필수값 
			inputJson = inputJson.replace("${slave.resources.requests.memory}", slaveMemory);// input *******   필수값 
			inputJson = inputJson.replace("${master.resources.limits.cpu}", masterCpu);// input , *******   필수값  
			inputJson = inputJson.replace("${master.resources.limits.memory}", masterMemory);// input *******   필수값 
			inputJson = inputJson.replace("${slave.resources.limits.cpu}", slaveCpu);// input*******   필수값 
			inputJson = inputJson.replace("${slave.resources.limits.memory}", slaveMemory);// input *******   필수값 
			
			valuesBuilder.setRaw(inputJson);
			
			log.info(service.getServiceName() + " update start.");
			
			final Future<UpdateReleaseResponse> releaseFuture = releaseManager.update(requestBuilder, chart);
			final Release release = releaseFuture.get().getRelease();
			
			if (release != null) {
				ReleaseMetaData releaseMeta = releaseRepository.findByReleaseName(service.getServiceName());
				if(releaseMeta == null) {
					releaseMeta = new ReleaseMetaData();
				}
				releaseMeta.setAction("UPDATE");
				releaseMeta.setApp(release.getChart().getMetadata().getName());
				releaseMeta.setAppVersion(release.getChart().getMetadata().getAppVersion());
				releaseMeta.setChartVersion(release.getChart().getMetadata().getVersion());
				releaseMeta.setChartName(release.getChart().getMetadata().getName());
				releaseMeta.setCreateTime(new Date(release.getInfo().getFirstDeployed().getSeconds() * 1000L));
				releaseMeta.setNamespace(service.getNamespace());
				releaseMeta.setReleaseName(service.getServiceName());
				releaseMeta.setStatus(release.getInfo().getStatus().getCode().name());
				releaseMeta.setDescription(release.getInfo().getDescription());
				releaseMeta.setInputValues(valuesBuilder.getRaw());
				releaseMeta.setNotes(release.getInfo().getStatus().getNotes());
				releaseMeta.setManifest(release.getManifest());
				releaseMeta.setUpdateTime(new Date(System.currentTimeMillis()));
				
				log.info(new Gson().toJson(releaseMeta));
				
				releaseRepository.save(releaseMeta);
			}
			
			log.info(service.getServiceName() + " update success!");
			result = new Result(txId, IResult.RUNNING, historyValue).putValue(IResult.UPDATE, release);
		} catch (FileNotFoundException | KubernetesClientException e) {
			log.error(e.getMessage(), e);
			
			if (e.getMessage().indexOf("Unauthorized") > -1) {
				return new Result(txId, Result.UNAUTHORIZED, "클러스터에 접근이 불가하거나 인증에 실패 했습니다.", null);
			} else {
				return new Result(txId, Result.UNAUTHORIZED, e.getMessage(), e);
			}
		} catch (Exception e) {
			log.error(e.getMessage(), e);
			return Result.RESULT_FAIL(txId, e);
		} finally {
			if (releaseManager != null) {
				try {
					releaseManager.close();
				} catch (IOException e) {
					log.error(e.getMessage(), e);
				}
			}
		}
		
		return result;
	}

	/**
	 * 
	 */
	public Result getDBVariables(final String txId, final String namespace, final String releaseName) {
		Result result = Result.RESULT_OK(txId);

		Iterable<Mycnf> findAll = configRepository.findAll();
		
		if(findAll == null || !findAll.iterator().hasNext()) {
			InitData.initData(configRepository);
			findAll = configRepository.findAll();
		} else {
			// 2019-10-04 수정
			// 기본 값 동기화 (추가된경우)
			int count = 0;
			for (Mycnf mycnf : findAll) {
				 count++;
			}
			
			if(InitData.defaultConfigMapSize() != count) {
				InitData.initData(configRepository);
				findAll = configRepository.findAll();
			}
		}
		
		Map<String, Mycnf> mycnfMap = new TreeMap<>();
		for (Mycnf mycnf : findAll) {
			mycnfMap.put(mycnf.getName(), mycnf);
		}
		
		Map<String, String> systemConfigMap = new HashMap<>();
		
		try {
			List<ConfigMap> configMaps = K8SUtil.getConfigMaps(namespace, releaseName);
			if(configMaps != null && !configMaps.isEmpty()) {
				ConfigMap map = configMaps.get(0);
				Map<String, String> data = map.getData();
				String cnf = data.get("my.cnf");
				String[] split = cnf.split("\n");
				
				for (String line : split) {
					String[] params = line.split("=");
					if(params.length == 2) {
						String key = params[0].trim();
						String value = params[1].trim();
						if(key.startsWith("#")) {
							continue;
						}
						systemConfigMap.put(key, value);
					}
				}
				
				for (Iterator<String> iterator = mycnfMap.keySet().iterator(); iterator.hasNext();) {
					String key = iterator.next();
					Mycnf mycnf = mycnfMap.get(key);
					
					String value = systemConfigMap.get(key);
					if (value != null && !value.isEmpty()) {
						if(mycnf != null) {
							mycnf.setValue(value);
						}
					}
				}
			}
		} catch (Exception e) {
			log.error(e.getMessage(), e);

			return Result.RESULT_FAIL(txId, e);
		}
		
		result = new Result(txId, IResult.OK, "");
		result.putValue(IResult.MARIADB_CONFIG, mycnfMap.values());
		
		return result;
	}
	
	public Result getAllDBVariables(final String txId, final String namespace, final String releaseName) {
		// TODO
		return null;
	}

	/* (non-Javadoc)
	 * @see com.zdb.core.service.ZDBRestService#getPersistentVolumeClaims(java.lang.String)
	 */
	@Override
	public Result getPersistentVolumeClaims(final String namespace) throws Exception {
		try {
			List<PersistentVolumeClaim> pvcs = K8SUtil.getPersistentVolumeClaims(namespace);
			
			if (pvcs != null) {
				return new Result("", Result.OK).putValue(IResult.PERSISTENTVOLUMECLAIMS, pvcs);
			}
		} catch (KubernetesClientException e) {
			log.error(e.getMessage(), e);
			if (e.getMessage().indexOf("Unauthorized") > -1) {
				return new Result("", Result.UNAUTHORIZED, "클러스터에 접근이 불가하거나 인증에 실패 했습니다.", null);
			} else {
				return new Result("", Result.UNAUTHORIZED, e.getMessage(), e);
			}
		} catch (Exception e) {
			log.error(e.getMessage(), e);
			return new Result("", Result.ERROR, e.getMessage(), e);
		}

		return new Result("", Result.OK).putValue(IResult.PERSISTENTVOLUMECLAIMS, "");
	}
	
	/* (non-Javadoc)
	 * @see com.zdb.core.service.ZDBRestService#getPersistentVolumeClaim(java.lang.String, java.lang.String)
	 */
	@Override
	public Result getPersistentVolumeClaim(final String namespace, final String pvcName) throws Exception {
		try {
			PersistentVolumeClaim pvc = K8SUtil.getPersistentVolumeClaim(namespace, pvcName);
			
			if (pvc != null) {
				return new Result("", Result.OK).putValue(IResult.PERSISTENTVOLUMECLAIM, pvc);
			}
		} catch (KubernetesClientException e) {
			log.error(e.getMessage(), e);
			if (e.getMessage().indexOf("Unauthorized") > -1) {
				return new Result("", Result.UNAUTHORIZED, "클러스터에 접근이 불가하거나 인증에 실패 했습니다.", null);
			} else {
				return new Result("", Result.UNAUTHORIZED, e.getMessage(), e);
			}
		} catch (Exception e) {
			log.error(e.getMessage(), e);
			return new Result("", Result.ERROR, e.getMessage(), e);
		}

		return new Result("", Result.ERROR);
	}

	public Result getDBInstanceAccounts(String txId, String namespace, String serviceName) throws Exception {
		Result result = Result.RESULT_OK(txId);
		
		try {
			List<ZDBMariaDBAccount> accounts = MariaDBAccount.getAccounts(zdbMariaDBAccountRepository, namespace, serviceName);
			if (accounts == null || accounts.isEmpty()) {
				log.warn("no account. namespace: {}, serviceName: {}", namespace, serviceName);
				result.putValue(IResult.ACCOUNTS, Collections.emptyList());
			} else {
				result.putValue(IResult.ACCOUNTS, accounts);
			}
		} catch (Exception e) {
			log.error(e.getMessage(), e);
			return Result.RESULT_FAIL(txId, e);
		}

		return result;
	}

	public Result createDBUser(final String txId, final String namespace, final String serviceName, final ZDBMariaDBAccount account) throws Exception {
		Result result = Result.RESULT_OK(txId);
		
		try {
			// TODO:
			if (null == MariaDBAccount.createAccount(zdbMariaDBAccountRepository, namespace, serviceName, account)) {
				log.error("FAIL: creating new account. namespace: {}, serviceName: {}, accountId: {}", namespace, serviceName, account.getUserId());
				Exception e = new Exception("creating new account failed. accountId: " + account.getUserId());
				result = Result.RESULT_FAIL(txId, e);
				throw e;
			}
			result.putValue(IResult.ACCOUNT, account);
		} catch (Exception e) {
			log.error(e.getMessage(), e);

			return Result.RESULT_FAIL(txId, e);
		}

		return result;
	}
	
	public Result updateDBUser(String txId, String namespace, String serviceName, ZDBMariaDBAccount account)
			throws Exception {
		Result result = Result.RESULT_OK(txId);

		try {
			ZDBMariaDBAccount accountBefore = MariaDBAccount.getAccount(zdbMariaDBAccountRepository, namespace, serviceName, account.getUserId());
			if (accountBefore == null) {
				return Result.RESULT_FAIL(txId, new Exception("no user. userId: " + account.getUserId()));
			}

			if (null == MariaDBAccount.updateAccount(zdbMariaDBAccountRepository, namespace, serviceName, accountBefore, account)) {
				log.error("FAIL: cannot update an account. namespace: {}, serviceName: {}, accountId: {}", namespace, serviceName, account.getUserId());
				return Result.RESULT_FAIL(txId, new Exception("cannot update an account. userId: " + account.getUserId()));
			}
			
			result.putValue(IResult.ACCOUNT, account);
		} catch (Exception e) {
			log.error(e.getMessage(), e);

			return Result.RESULT_FAIL(txId, e);
		}

		return result;
	}
	
	public Result updateAdminPassword(String txId, String namespace, String serviceName, String newPwd)
			throws Exception {
		Result result = Result.RESULT_OK(txId);

		try {
			String userId = "admin";

			if (null == MariaDBAccount.updateAdminPassword(zdbMariaDBAccountRepository, namespace, serviceName, newPwd)) {
				log.error("FAIL: cannot update an account. namespace: {}, serviceName: {}, accountId: {}", namespace, serviceName, userId);
				return Result.RESULT_FAIL(txId, new Exception("cannot update an account. userId: " + userId));
			}
			
		} catch (Exception e) {
			log.error(e.getMessage(), e);

			return Result.RESULT_FAIL(txId, e);
		}

		return result;
	}
	
	public Result deleteDBInstanceAccount(String txId, String namespace, String serviceName, String accountId)
			throws Exception {
		Result result = Result.RESULT_OK(txId);
		
		try {
			MariaDBAccount.deleteAccount(zdbMariaDBAccountRepository, namespace, serviceName, accountId);
		} catch (Exception e) {
			log.error(e.getMessage(), e);

			return Result.RESULT_FAIL(txId, e);
		}
		return result;
	}
	
	@Override
	public Result deletePersistentVolumeClaimsService(String txid, String namespace, String serviceName, String pvcName) throws Exception {
		// TODO Auto-generated method stub
		return null;
	}

	/**
	 * @param namespace
	 * @param serviceType
	 * @param serviceName
	 * @return
	 * @throws Exception
	 */
	public Result getConnectionInfo(String namespace, String serviceType, String serviceName) throws Exception {
		ConnectionInfo info = new ConnectionInfo("mariadb");

		List<Secret> secrets = K8SUtil.getSecrets(namespace, serviceName);
		if( secrets == null || secrets.isEmpty()) {
			return new Result("", Result.ERROR).putValue(IResult.CONNECTION_INFO, info);
		}
		
		for(Secret secret : secrets) {
			Map<String, String> secretData = secret.getData();
			String credetial = secretData.get("mariadb-password");
			
			info.setCredetial(credetial);
		}
		
		List<Connection> connectionList = new ArrayList<>();
		
		info.setConnectionList(connectionList);
		
		List<io.fabric8.kubernetes.api.model.Service> services = K8SUtil.getServices(namespace, serviceName);
		for (io.fabric8.kubernetes.api.model.Service service : services) {
			Connection connection = new Connection();
			
			try {
				String role = service.getMetadata().getLabels().get("component");
				if(!("master".equals(role)||"slave".equals(role)
				)){
					continue;
				}				
				
				connection.setRole(role);
				connection.setServiceName(service.getMetadata().getName());
				connection.setConnectionType(service.getMetadata().getAnnotations().get("service.kubernetes.io/ibm-load-balancer-cloud-provider-ip-type"));
				
				String ip = new String();
				
				if ("LoadBalancer".equals(service.getSpec().getType())) {
					List<LoadBalancerIngress> ingress = service.getStatus().getLoadBalancer().getIngress();
					if(ingress != null && ingress.size() > 0) {
						ip = ingress.get(0).getIp();
					} else {
						ip = service.getMetadata().getName()+"."+service.getMetadata().getNamespace();
					}
				} else if ("ClusterIP".equals(service.getSpec().getType())) {
					ip = service.getSpec().getClusterIP();
				}
				
				connection.setIpAddress(ip);

				ReleaseMetaData releaseMetaData = releaseRepository.findByReleaseName(serviceName);
				if( releaseMetaData != null) {
					info.setDbName(releaseMetaData.getDbname());
				} 
				
				if(info.getDbName() == null || info.getDbName().length() == 0) {
					String mariaDBDatabase = MariaDBAccount.getMariaDBDatabase(namespace, serviceName);
					info.setDbName(mariaDBDatabase);
				}				
				
				List<ServicePort> ports = service.getSpec().getPorts();
				for (ServicePort serivicePort : ports) {
					if (!"mysql".equals(serivicePort.getName())) {
						break;
					} else {
						connection.setPort(serivicePort.getPort().intValue());
						info.setPort(serivicePort.getPort().intValue());
						break;
					}
				}
				
				String connString = info.getConnectionString(connection);
				String connLine = info.getConnectionLine(connection);
				
				connection.setConnectionString(connString);
				connection.setConnectionLine(connLine);
				
				connectionList.add(connection);
			} catch (Exception e) {
				String clusterIP = service.getSpec().getClusterIP();
				connection.setIpAddress(clusterIP);
				if(connection.getConnectionType() == null) {
					connection.setConnectionType("private");
				}

				List<ServicePort> ports = service.getSpec().getPorts();
				for (ServicePort serivicePort : ports) {
					if ("mysql".equals(serivicePort.getName())) {
						connection.setPort(serivicePort.getPort().intValue());
						info.setPort(serivicePort.getPort().intValue());
						break;
					}
				}
				connectionList.add(connection);
			}
		}			
		
		return new Result("", Result.OK).putValue(IResult.CONNECTION_INFO, info);

	}

	@Override
	public Result getServiceCheckAlive(String namespace, String serviceType, String serviceName) throws Exception {
		// TODO Auto-generated method stub
		return null;
	}

	/**
	 * @param txId
	 * @param namespace
	 * @param serviceName
	 * @param config
	 * @return
	 * @throws Exception
	 */
	public Result updateConfig(String txId, String namespace, String serviceName, Map<String, String> config) throws Exception {
		Result result = null;
		String historyValue = "";
		try (final DefaultKubernetesClient client = K8SUtil.kubernetesClient()) {
			Iterable<Mycnf> findAll = configRepository.findAll();

			if (findAll == null || !findAll.iterator().hasNext()) {
				InitData.initData(configRepository);
				findAll = configRepository.findAll();
			} else {
				// 2019-10-04 수정
				// 기본 값 동기화 (추가된경우)
				int count = 0;
				for (Mycnf mycnf : findAll) {
					 count++;
				}
				
				if(InitData.defaultConfigMapSize() != count) {
					InitData.initData(configRepository);
					findAll = configRepository.findAll();
				}
			}

			InputStream is = new ClassPathResource("mariadb/mycnf.template").getInputStream();
			String inputValue = IOUtils.toString(is, StandardCharsets.UTF_8.name());

			for (Mycnf mycnf : findAll) {
				inputValue = inputValue.replace("${" + mycnf.getName() + "}", config.get(mycnf.getName()) == null ? mycnf.getValue() : config.get(mycnf.getName()));
			}

			// 2018-10-04 추가
			// 환경설정 변경 이력 
			historyValue = compareVariables(namespace, serviceName, config);
			
			List<ConfigMap> items = client.inNamespace(namespace).configMaps().withLabel("release", serviceName).list().getItems();
			
			Map<String, String> beforeDataMap = new HashMap<>();
			
			for (ConfigMap configMap : items) {
				String configMapName = configMap.getMetadata().getName();
				String beforeValue = configMap.getData().get("my.cnf");

				beforeDataMap.put(configMapName, beforeValue);
				client.configMaps().inNamespace(namespace).withName(configMapName).edit().addToData("my.cnf", inputValue).done();
			}

			// 2018-12-04 환경설정 저장시 즉시 반영이 아닌 설정값 저장만 수행하고 재시작은 사용자가 수행.
			// shutdown and pod delete (restart)
//			MariaDBShutDownUtil.getInstance().doShutdownAndDeleteAllPods(namespace, serviceName);

			// 2018-12-04 my.cnf 변경된 값 저장(master, slave)
			saveHistory(namespace, serviceName, beforeDataMap);
			
			result = new Result(txId, IResult.OK, historyValue);

		} catch (Exception e) {
			log.error(e.getMessage(), e);
			result = new Result(txId, IResult.ERROR, "환경설정 변경 오류 - " + e.getMessage());
			if (!historyValue.isEmpty()) {
				result.putValue(Result.HISTORY, historyValue);
			}
		}

		return result;
	}
	
	/**
	 * 환경 설정 변경값 저장.
	 */
	private void saveHistory(String namespace, String serviceName, Map<String, String> beforeDataMap) throws Exception {
		try {
			List<ConfigMap> cmItems = K8SUtil.kubernetesClient().inNamespace(namespace).configMaps().withLabel("release", serviceName).list().getItems();
			for (ConfigMap configMap : cmItems) {

				String configMapName = configMap.getMetadata().getName();
				String value = configMap.getData().get("my.cnf");
				
				String beforeValue = beforeDataMap.get(configMapName);

				ZDBMariaDBConfig config = new ZDBMariaDBConfig();
				config.setConfigMapName(configMapName);
				config.setReleaseName(serviceName);
				config.setConfigMapName(configMapName);
				config.setBeforeValue(beforeValue);
				config.setAfterValue(value);
				config.setDate(DateUtil.currentDate());

				zdbMariaDBConfigRepository.save(config);

				log.info("{} 의 my.cnf 저장 완료.", configMapName);
			}
		} catch (Exception e) {
			log.error("Configmap (my.cnf) 저장 중 오류 - " + e.getMessage(), e);
			throw e;
		}
	}

	@Override
	public Result updateScaleOut(String txId, ZDBEntity zdbEntity) throws Exception {
		return null;
	}
	
	@Override
	public Result restartService(String txId, ZDBType dbType, String namespace, String serviceName) {
		Result result = null;

		try {
			// shutdown and pod delete (restart)
			MariaDBShutDownUtil.getInstance().doShutdownAndDeleteAllPods(namespace, serviceName);
			result = new Result(txId, IResult.OK, "서비스 재시작 요청됨");
		} catch (Exception e) {
			log.error(e.getMessage(), e);
			result = new Result(txId, IResult.ERROR, "서비스 재시작 오류. - " + e.getMessage());
		}

		return result;
	}
	
	/* (non-Javadoc)
	 * @see com.zdb.core.service.AbstractServiceImpl#reStartPod(java.lang.String, java.lang.String, java.lang.String, java.lang.String)
	 */
	public Result rstartPod(String txId, String namespace, String serviceName, String podName) throws Exception {
		Result result = null;

		try {
			// shutdown and pod delete (restart)
			MariaDBShutDownUtil.getInstance().doShutdownAndDeletePod(namespace, serviceName, podName);
			result = new Result(txId, IResult.OK, "Pod 재시작 요청됨");
		} catch (Exception e) {
			log.error(e.getMessage(), e);
			result = new Result(txId, IResult.ERROR, "Pod 재시작 오류. - " + e.getMessage());
		}

		return result;
	}
	
	public Result getMycnf(String namespace, String releaseName) {
		String cnf = "";
		try {
			List<ConfigMap> configMaps = K8SUtil.getConfigMaps(namespace, releaseName);
			
			if(configMaps != null && !configMaps.isEmpty()) {
				ConfigMap map = configMaps.get(0);
				Map<String, String> data = map.getData();
				cnf = data.get("my.cnf");
				
				String[] mycnf = cnf.split("\n");
				
				return new Result("", Result.OK).putValue(IResult.MY_CNF, mycnf);
			}
		} catch (KubernetesClientException e) {
			log.error(e.getMessage(), e);
			if (e.getMessage().indexOf("Unauthorized") > -1) {
				return new Result("", Result.UNAUTHORIZED, "클러스터에 접근이 불가하거나 인증에 실패 했습니다.", null);
			} else {
				return new Result("", Result.UNAUTHORIZED, e.getMessage(), e);
			}
		} catch (Exception e) {
			log.error(e.getMessage(), e);
			return new Result("", Result.ERROR, e.getMessage(), e);
		}
		
		return new Result("", Result.ERROR);
	}

	@Override
	public Result getUserGrants(String namespace, String serviceType, String releaseName) {
		try {
			List<DBUser> userGrants = MariaDBAccount.getUserGrants(namespace, releaseName);
			
			return new Result("", Result.OK).putValue(IResult.USER_GRANTS, userGrants);
		} catch (KubernetesClientException e) {
			log.error(e.getMessage(), e);
			if (e.getMessage().indexOf("Unauthorized") > -1) {
				return new Result("", Result.UNAUTHORIZED, "클러스터에 접근이 불가하거나 인증에 실패 했습니다.", null);
			} else {
				return new Result("", Result.UNAUTHORIZED, e.getMessage(), e);
			}
		} catch (Exception e) {
			log.error(e.getMessage(), e);
			return new Result("", Result.ERROR, e.getMessage(), e);
		}
	}
	
	@Override
	public Result getSlowLog(String namespace, String podName) throws Exception {
		try {
			DefaultKubernetesClient client = K8SUtil.kubernetesClient();
			
			String app = client.pods().inNamespace(namespace).withName(podName).get().getMetadata().getLabels().get("app");
			String log = "";
			if ("redis".equals(app)) {
				
				return new Result("", Result.OK).putValue(IResult.SLOW_LOG, "");
			} else if ("mariadb".equals(app)) {
				String slowlogPath = getLogPath(namespace, podName, "slow_query_log_file");
				if(slowlogPath == null || slowlogPath.isEmpty()) {
					slowlogPath = "/bitnami/mariadb/logs/maria_slow.log";
				}
				
				log = new ZDBLogViewer().getTailLog(namespace, podName, "mariadb", 1000, slowlogPath);
				if (!log.isEmpty()) {
					String[] errorLog = log.split("\n");

					return new Result("", Result.OK).putValue(IResult.SLOW_LOG, errorLog);
				}
			}
			
		} catch (KubernetesClientException e) {
			log.error(e.getMessage(), e);
			if (e.getMessage().indexOf("Unauthorized") > -1) {
				return new Result("", Result.UNAUTHORIZED, "클러스터에 접근이 불가하거나 인증에 실패 했습니다.", null);
			} else {
				return new Result("", Result.UNAUTHORIZED, e.getMessage(), e);
			}
		} catch (Exception e) {
			log.error(e.getMessage(), e);
			return new Result("", Result.ERROR, e.getMessage(), e);
		}
		
		return new Result("", Result.ERROR);
	}
	
	@Override
	public Result getSlowLogDownload(String namespace, String podName) throws Exception {
		try {
			DefaultKubernetesClient client = K8SUtil.kubernetesClient();
			
			String app = client.pods().inNamespace(namespace).withName(podName).get().getMetadata().getLabels().get("app");
			String log = "";
			if ("redis".equals(app)) {
				
				return new Result("", Result.OK).putValue(IResult.SLOW_LOG, "");
			} else if ("mariadb".equals(app)) {
				String slowlogPath = getLogPath(namespace, podName, "slow_query_log_file");
				if(slowlogPath == null || slowlogPath.isEmpty()) {
					slowlogPath = "/bitnami/mariadb/logs/maria_slow.log";
				}
				
				log = new ZDBLogViewer().getSlowLogDownload(namespace, podName, "mariadb", slowlogPath);
				if (!log.isEmpty()) {
					String[] errorLog = log.split("\n");

					return new Result("", Result.OK).putValue(IResult.SLOW_LOG, errorLog);
				}
			}
			
		} catch (KubernetesClientException e) {
			log.error(e.getMessage(), e);
			if (e.getMessage().indexOf("Unauthorized") > -1) {
				return new Result("", Result.UNAUTHORIZED, "클러스터에 접근이 불가하거나 인증에 실패 했습니다.", null);
			} else {
				return new Result("", Result.UNAUTHORIZED, e.getMessage(), e);
			}
		} catch (Exception e) {
			log.error(e.getMessage(), e);
			return new Result("", Result.ERROR, e.getMessage(), e);
		}
		
		return new Result("", Result.ERROR);
	}
	
	private String compareVariables(String namespace, String releaseName, Map<String, String> newConfig) {
		Map<String, String> systemConfigMap = new HashMap<>();
		
		StringBuffer sb = new StringBuffer();
		
		try {
			List<ConfigMap> configMaps = K8SUtil.getConfigMaps(namespace, releaseName);
			if(configMaps != null && !configMaps.isEmpty()) {
				ConfigMap map = configMaps.get(0);
				Map<String, String> data = map.getData();
				String cnf = data.get("my.cnf");
				String[] split = cnf.split("\n");
				
				for (String line : split) {
					String[] params = line.split("=");
					if(params.length == 2) {
						String key = params[0].trim();
						String value = params[1].trim();
						if(key.startsWith("#")) {
							continue;
						}
						systemConfigMap.put(key, value);
						
						String newValue = newConfig.get(key);
						
						if(value != null && newValue != null && !value.equals(newValue)) {
							sb.append(key).append(" : ").append(value).append(" → ").append(newValue).append("\n");
						}
					}
				}
			}
		} catch (Exception e) {
			log.error(e.getMessage(), e);
		}
		
		return sb.toString();
	}
	
	public Result updateStorageScale(String txId, String namespace, String serviceType, String serviceName, String pvcSize) throws Exception {

		Result result = new Result(txId);

		String historyValue = "";
		
		try {
			// 서비스 명 체크
			ReleaseMetaData releaseMetaData = releaseRepository.findByReleaseName(serviceName);
			if( releaseMetaData == null) {
				String msg = "서비스가 존재하지 않습니다.";
				return new Result(txId, IResult.ERROR, msg);
			}

			String accessMode = "ReadWriteOnce";
			String billingType = "hourly";
			String storageClass = "ibmc-block-silver";

			String statefulsetName = "";

			Pod pod = k8sService.getPod(namespace, serviceName, "master");
			
			if(pod != null) {
				List<Volume> volumes = pod.getSpec().getVolumes();
				try {
					for (Volume volume : volumes) {
						if("data".equals(volume.getName())) {
							String claimName = volume.getPersistentVolumeClaim().getClaimName();
							PersistentVolumeClaim persistentVolumeClaim = K8SUtil.kubernetesClient().inNamespace(namespace).persistentVolumeClaims().withName(claimName).get();
							storageClass = persistentVolumeClaim.getSpec().getStorageClassName();
							Quantity quantity = persistentVolumeClaim.getSpec().getResources().getRequests().get("storage");
							String amount = quantity.getAmount();
							
							
							// 스토리지 사이즈 변경 이력 
							historyValue = String.format("스토리지 스케일업 : %s -> %s", amount, pvcSize);
							break;
						}
					}
				} catch (Exception e) {
					log.error(e.getMessage(), e);
				}
			} else {
				
			}
			
			log.info(serviceName + " update start.");
			
			List<Pod> pods = k8sService.getPods(namespace, serviceName);
			if (pods.size() > 1) {
				Descending descending = new Descending();
				Collections.sort(pods, descending);
			}
			
			List<StatefulSet> statefulSets = k8sService.getStatefulSets(namespace, serviceName);

			List<Job> jobList = new ArrayList<>();
			for (Pod p : pods) {
				
				for (StatefulSet sts : statefulSets) {
					statefulsetName = sts.getMetadata().getName();
					if(p.getMetadata().getName().startsWith(statefulsetName)) {
						break;
					}
				}
				
				if(statefulsetName == null || statefulsetName.isEmpty()) {
					log.error(p.getMetadata().getName() + " 매칭되는 StatefulSet이 존재하지 않습니다.");
					break;
				}
				
				JobParameter param = new JobParameter();
				param.setNamespace(namespace);
				param.setPodName(p.getMetadata().getName());
				param.setServiceType(serviceType);
				param.setServiceName(serviceName);
				param.setAccessMode(accessMode == null ? "ReadWriteOnce" : accessMode);
				param.setBillingType(billingType == null ? "hourly" : billingType);
				param.setSize(pvcSize);
				param.setStorageClass(storageClass == null ? "ibmc-block-silver" : storageClass);
				param.setStatefulsetName(statefulsetName);

				CreatePersistentVolumeClaimsJob job1 = new CreatePersistentVolumeClaimsJob(param);
				ShutdownServiceJob              job2 = new ShutdownServiceJob(param);
				DataCopyJob                     job3 = new DataCopyJob(param);
				StartServiceJob                 job4 = new StartServiceJob(param);
				
				jobList.add(job1);
				jobList.add(job2);
				jobList.add(job3);
				jobList.add(job4);

			}
			
			if (jobList.size() > 0) {
				CountDownLatch latch = new CountDownLatch(jobList.size());

				JobExecutor storageScaleExecutor = new JobExecutor(latch);

				final String _historyValue = historyValue;

				EventListener eventListener = new EventListener() {

					@Override
					public void onEvent(Job job, String event) {
						log.info(job.getJobName() + "  onEvent - " + event);
					}

					@Override
					public void status(Job job, String status) {
						//log.info(job.getJobName() + " : " + status);
					}

					@Override
					public void done(Job job, JobResult code, String message, Throwable e) {
						RequestEvent event = new RequestEvent();

						event.setTxId(txId);
						event.setStartTime(new Date(System.currentTimeMillis()));
						event.setServiceType(serviceType);
						event.setNamespace(namespace);
						event.setServiceName(serviceName);
						event.setOperation(RequestEvent.STORAGE_SCALE_UP);
						event.setUserId("SYSTEM");
						if (code == JobResult.ERROR) {
							event.setStatus(IResult.ERROR);
							event.setResultMessage(job.getJobName() + " 처리 중 오류가 발생했습니다. (" + e.getMessage() + ")");
						} else {
							event.setStatus(IResult.OK);
							if(message == null || message.isEmpty()) {
								event.setResultMessage(job.getJobName() + " 정상 처리되었습니다.");
							} else {
								event.setResultMessage(message);
							}
						}
						event.setHistory(_historyValue);
						event.setEndTime(new Date(System.currentTimeMillis()));
						ZDBRepositoryUtil.saveRequestEvent(zdbRepository, event);
						
						// 오류시 원복 처리...
//						CreatePersistentVolumeClaimsJob job1 = new CreatePersistentVolumeClaimsJob(param); 0,4
//						ShutdownServiceJob              job2 = new ShutdownServiceJob(param); 1,5
//						DataCopyJob                     job3 = new DataCopyJob(param); 2,6
//						StartServiceJob                 job4 = new StartServiceJob(param); 3, 7
						if (code == JobResult.ERROR) {
							if(job.getClass().getName().equals(ShutdownServiceJob.class.getName())) {
								if(jobList.indexOf(job) == 1) {
								    //  1번 job replica 0 -> 1
									JobParameter param = job.getJobParameter();
									String stsName = param.getStatefulsetName();
									JobParameter p = new JobParameter();
									p.setNamespace(param.getNamespace());
									p.setStatefulsetName(param.getStatefulsetName());
									p.setTargetPvc(param.getTargetPvc());
									
									RecoveryStatefulsetJob recoveryJob = new RecoveryStatefulsetJob(p);
									
									new Thread(recoveryJob, "Recovery Statefulset - "+stsName).start();
									
								} else if(jobList.indexOf(job) == 5) {
									//    5번 job replica 0 -> 1
									{
										JobParameter param = job.getJobParameter();
										String stsName = param.getStatefulsetName();
										JobParameter p = new JobParameter();
										p.setNamespace(param.getNamespace());
										p.setStatefulsetName(param.getStatefulsetName());
										p.setTargetPvc(param.getTargetPvc());
										
										RecoveryStatefulsetJob recoveryJob = new RecoveryStatefulsetJob(p);
										
										new Thread(recoveryJob, "Recovery Statefulset - "+stsName).start();
									}
									//    1번 job replica 0 -> 1	  & org pvc mount		
									{
										Job j = jobList.get(1);
										JobParameter param = j.getJobParameter();
										String stsName = param.getStatefulsetName();

										JobParameter p = new JobParameter();
										p.setNamespace(param.getNamespace());
										p.setStatefulsetName(param.getStatefulsetName());
										p.setSourcePvc(param.getSourcePvc());
										p.setTargetPvc(param.getTargetPvc());
										
										RecoveryStatefulsetJob recoveryJob = new RecoveryStatefulsetJob(p);
										
										new Thread(recoveryJob, "Recovery Statefulset - "+stsName).start();
									}
								}
								
								
							} else if(job.getClass().getName().equals(DataCopyJob.class.getName())) {
								// copy pod exist ? pod delete : continue;
								JobParameter param = job.getJobParameter();
								String namespace = param.getNamespace();
								String stsName = param.getStatefulsetName();
								
								String copyPodName = DataCopyJob.CP_NAME + "-" + stsName;
								
								try {
									PodResource<Pod, DoneablePod> podResource = K8SUtil.kubernetesClient().inNamespace(namespace).pods().withName(copyPodName);
									Pod copyPod = podResource.get();
									if(copyPod != null) {
										podResource.delete();
									}
								} catch (Exception e1) {
									log.error(e.getMessage(), e);
								}
								// 
								// jobList.indexof == 6 ? 
								//    6번 job replica 0 -> 1 & org pvc mount
								//    2번 job replica 0 -> 1 & org pvc mount
								// else
								//    2번 job replica 0 -> 1 & org pvc mount
								
								if(jobList.indexOf(job) == 2) {
									JobParameter p = new JobParameter();
									p.setNamespace(namespace);
									p.setStatefulsetName(stsName);
									p.setSourcePvc(param.getSourcePvc());
									p.setTargetPvc(param.getTargetPvc());
									
									RecoveryStatefulsetJob recoveryJob = new RecoveryStatefulsetJob(p);
									
									new Thread(recoveryJob, "Recovery Statefulset - "+stsName).start();
								} else if(jobList.indexOf(job) == 6) {
									{
										// 6번 job replica 0 -> 1 & org pvc mount
										JobParameter p = new JobParameter();
										p.setNamespace(namespace);
										p.setStatefulsetName(stsName);
										p.setSourcePvc(param.getSourcePvc());
										p.setTargetPvc(param.getTargetPvc());
										
										RecoveryStatefulsetJob recoveryJob = new RecoveryStatefulsetJob(p);
										
										new Thread(recoveryJob, "Recovery Statefulset - "+stsName).start();
									}
									
									{
										// 2번 job replica 0 -> 1 & org pvc mount
										Job j = jobList.get(2);
										param = j.getJobParameter();
										stsName = param.getStatefulsetName();
										
										JobParameter p = new JobParameter();
										p.setNamespace(param.getNamespace());
										p.setStatefulsetName(param.getStatefulsetName());
										p.setSourcePvc(param.getSourcePvc());
										p.setTargetPvc(param.getTargetPvc());
										
										RecoveryStatefulsetJob recoveryJob = new RecoveryStatefulsetJob(p);
										
										new Thread(recoveryJob, "Recovery Statefulset - "+stsName).start();
									}
								}
								
							} else if(job.getClass().getName().equals(StartServiceJob.class.getName())) {
								// copy pod exist ? pod delete : continue;
								// 
								// jobList.indexof == 7 ? 
								//    7번 job replica 0 -> 1 & org pvc mount
								//    3번 job replica 0 -> 1 & org pvc mount
								// else
								//    3번 job replica 0 -> 1 & org pvc mount
								
								if(jobList.indexOf(job) == 3) {
									// 3번 job replica 0 -> 1 & org pvc mount
									JobParameter param = job.getJobParameter();
									String namespace = param.getNamespace();
									String stsName = param.getStatefulsetName();
									
									JobParameter p = new JobParameter();
									p.setNamespace(namespace);
									p.setStatefulsetName(stsName);
									p.setSourcePvc(param.getSourcePvc());
									p.setTargetPvc(param.getTargetPvc());
									
									RecoveryStatefulsetJob recoveryJob = new RecoveryStatefulsetJob(p);
									
									new Thread(recoveryJob, "Recovery Statefulset - "+stsName).start();
								} else if(jobList.indexOf(job) == 7) {
									{
										// 7번 job replica 0 -> 1 & org pvc mount
										JobParameter param = job.getJobParameter();
										String namespace = param.getNamespace();
										String stsName = param.getStatefulsetName();
										
										JobParameter p = new JobParameter();
										p.setNamespace(namespace);
										p.setStatefulsetName(stsName);
										p.setSourcePvc(param.getSourcePvc());
										p.setTargetPvc(param.getTargetPvc());
										
										RecoveryStatefulsetJob recoveryJob = new RecoveryStatefulsetJob(p);
										
										new Thread(recoveryJob, "Recovery Statefulset - "+stsName).start();
									}
									
									{
										//3번 job replica 0 -> 1 & org pvc mount
										Job j = jobList.get(3);
										
										JobParameter param = j.getJobParameter();
										String namespace = param.getNamespace();
										String stsName = param.getStatefulsetName();
										
										JobParameter p = new JobParameter();
										p.setNamespace(namespace);
										p.setStatefulsetName(stsName);
										p.setSourcePvc(param.getSourcePvc());
										p.setTargetPvc(param.getTargetPvc());
										
										RecoveryStatefulsetJob recoveryJob = new RecoveryStatefulsetJob(p);
										
										new Thread(recoveryJob, "Recovery Statefulset - "+stsName).start();
									}
								}
							}
						}
					}

				};

				JobHandler.addListener(eventListener);

				storageScaleExecutor.execTask(jobList.toArray(new Job[] {}));

				log.info(serviceName + " 스토리지 스케일업 요청.");
				result = new Result(txId, IResult.RUNNING, historyValue);
			} else {
				result = new Result(txId, IResult.ERROR, "스토리지 스케일업 오류.");
			}
		} catch (KubernetesClientException e) {
			log.error(e.getMessage(), e);

			if (e.getMessage().indexOf("Unauthorized") > -1) {
				return new Result(txId, Result.UNAUTHORIZED, "클러스터에 접근이 불가하거나 인증에 실패 했습니다.", null);
			} else {
				return new Result(txId, Result.UNAUTHORIZED, e.getMessage(), e);
			}
		} catch (Exception e) {
			log.error(e.getMessage(), e);
			return Result.RESULT_FAIL(txId, e);
		} finally {
		}
		
		return result;
	
	}
	
	class Descending implements Comparator<Pod> {
		 
	    @Override
	    public int compare(Pod o1, Pod o2) {
	    		return o2.getMetadata().getName().compareTo(o1.getMetadata().getName());
	    }
	 
	}
	
	@Override
	public Result serviceOn(String txId, String namespace, String serviceType, String serviceName, String stsName) throws Exception {
		Result result = new Result(txId);

		String historyValue = "";
		
		try {
			// 서비스 명 체크
			ReleaseMetaData releaseMetaData = releaseRepository.findByReleaseName(serviceName);
			if( releaseMetaData == null) {
				String msg = "서비스가 존재하지 않습니다.";
				return new Result(txId, IResult.ERROR, msg);
			}

			log.info(stsName + " service on.");
			
			List<Pod> pods = K8SUtil.getPods(namespace, serviceName);
			
			List<Job> jobList = new ArrayList<>();
			for (Pod p : pods) {
				
				if(p.getMetadata().getName().startsWith(stsName)) {
					
					boolean isReady = PodManager.isReady(p);
					
					if(isReady) {
						log.error("서비스가 실행 중입니다.");
						
						return new Result(txId, Result.ERROR, "서비스가 실행 중입니다.");
					}
					break;
				}
			}
				
			JobParameter param = new JobParameter();
			param.setNamespace(namespace);
			param.setServiceType(serviceType);
			param.setServiceName(serviceName);
			param.setStatefulsetName(stsName);
			param.setToggle(1);

			ServiceOnOffJob job1 = new ServiceOnOffJob(param);
			
			jobList.add(job1);
			
			if (jobList.size() > 0) {
				CountDownLatch latch = new CountDownLatch(jobList.size());

				JobExecutor storageScaleExecutor = new JobExecutor(latch);

				final String _historyValue = String.format("서비스 시작(%s)", stsName);

				EventListener eventListener = new EventListener() {

					@Override
					public void onEvent(Job job, String event) {
						log.info(job.getJobName() + "  onEvent - " + event);
					}

					@Override
					public void status(Job job, String status) {
						//log.info(job.getJobName() + " : " + status);
					}

					@Override
					public void done(Job job, JobResult code, String message, Throwable e) {
						if (jobList.contains(job)) {
							RequestEvent event = new RequestEvent();

							event.setTxId(txId);
							event.setStartTime(new Date(System.currentTimeMillis()));
							event.setServiceType(serviceType);
							event.setNamespace(namespace);
							event.setServiceName(serviceName);
							event.setOperation(RequestEvent.SERVICE_ON);
							event.setUserId("SYSTEM");
							try {
								if (code == JobResult.ERROR) {
									event.setStatus(IResult.ERROR);
									event.setResultMessage(job.getJobName() + " 처리 중 오류가 발생했습니다. (" + e.getMessage() + ")");
								} else {
									
									List<StatefulSet> statefulSets = K8SUtil.getStatefulSets(namespace, serviceName);
									metaDataCollector.save(statefulSets);
									
									event.setStatus(IResult.OK);
									if (message == null || message.isEmpty()) {
										event.setResultMessage(job.getJobName() + " 정상 처리되었습니다.");
									} else {
										event.setResultMessage(message);
									}
								}
								event.setHistory(_historyValue);
								event.setEndTime(new Date(System.currentTimeMillis()));
								ZDBRepositoryUtil.saveRequestEvent(zdbRepository, event);
	
								JobHandler.removeListener(this);
							
							// send websocket
							
								ServiceOverview serviceOverview = k8sService.getServiceWithName(namespace, serviceType, serviceName);
								
								Result r = Result.RESULT_OK.putValue(IResult.SERVICEOVERVIEW, serviceOverview);
								messageSender.convertAndSend("/service/" + serviceOverview.getServiceName(), r);
								
							} catch (MessagingException e1) {
								e1.printStackTrace();
							} catch (Exception e1) {
								e1.printStackTrace();
							}
							
						}
					}

				};

				JobHandler.addListener(eventListener);

				storageScaleExecutor.execTask(jobList.toArray(new Job[] {}));

				log.info(serviceName + " 서비스 시작 요청.");
				result = new Result(txId, IResult.RUNNING, stsName + "<br>서비스를 시작 합니다.");
			} else {
				result = new Result(txId, IResult.ERROR, "서비스 시작 실행 오류.");
			}
		} catch (KubernetesClientException e) {
			log.error(e.getMessage(), e);

			if (e.getMessage().indexOf("Unauthorized") > -1) {
				return new Result(txId, Result.UNAUTHORIZED, "클러스터에 접근이 불가하거나 인증에 실패 했습니다.", null);
			} else {
				return new Result(txId, Result.UNAUTHORIZED, e.getMessage(), e);
			}
		} catch (Exception e) {
			log.error(e.getMessage(), e);
			return Result.RESULT_FAIL(txId, e);
		} finally {
		}
		
		return result;
	}
	
	
	@Autowired
	private MetaDataCollector metaDataCollector;
	
	@Override
	public Result serviceOff(String txId, String namespace, String serviceType, String serviceName, String stsName) throws Exception {

		Result result = new Result(txId);

		String historyValue = "";
		
		try {
			// 서비스 명 체크
			ReleaseMetaData releaseMetaData = releaseRepository.findByReleaseName(serviceName);
			if( releaseMetaData == null) {
				String msg = "서비스가 존재하지 않습니다.";
				return new Result(txId, IResult.ERROR, msg);
			}

			log.info(stsName + " service shutdown.");
			
			List<Job> jobList = new ArrayList<>();
			JobParameter param = new JobParameter();
			param.setNamespace(namespace);
			param.setServiceType(serviceType);
			param.setServiceName(serviceName);
			param.setStatefulsetName(stsName);
			param.setToggle(0);

			ServiceOnOffJob job1 = new ServiceOnOffJob(param);
			
			jobList.add(job1);
			
			if (jobList.size() > 0) {
				CountDownLatch latch = new CountDownLatch(jobList.size());

				JobExecutor storageScaleExecutor = new JobExecutor(latch);

				final String _historyValue = String.format("서비스 종료(%s)", stsName);

				EventListener eventListener = new EventListener() {

					@Override
					public void onEvent(Job job, String event) {
						log.info(job.getJobName() + "  onEvent - " + event);
					}

					@Override
					public void status(Job job, String status) {
						//log.info(job.getJobName() + " : " + status);
					}

					@Override
					public void done(Job job, JobResult code, String message, Throwable e) {
						if (jobList.contains(job)) {
							RequestEvent event = new RequestEvent();

							event.setTxId(txId);
							event.setStartTime(new Date(System.currentTimeMillis()));
							event.setServiceType(serviceType);
							event.setNamespace(namespace);
							event.setServiceName(serviceName);
							event.setOperation(RequestEvent.SERVICE_OFF);
							event.setUserId("SYSTEM");
							try {
								if (code == JobResult.ERROR) {
									event.setStatus(IResult.ERROR);
									event.setResultMessage(job.getJobName() + " 처리 중 오류가 발생했습니다. " + e != null ? "["+e.getMessage()+"]" : "" + ")");
								} else {
									
									List<StatefulSet> statefulSets = K8SUtil.getStatefulSets(namespace, serviceName);
									metaDataCollector.save(statefulSets);
									
									event.setStatus(IResult.OK);
									if (message == null || message.isEmpty()) {
										event.setResultMessage(job.getJobName() + " 정상 처리되었습니다.");
									} else {
										event.setResultMessage(message);
									}
								}
								event.setHistory(_historyValue);
								event.setEndTime(new Date(System.currentTimeMillis()));
								ZDBRepositoryUtil.saveRequestEvent(zdbRepository, event);
								JobHandler.removeListener(this);
							
								ServiceOverview serviceOverview = k8sService.getServiceWithName(namespace, serviceType, serviceName);
								
								Result r = Result.RESULT_OK.putValue(IResult.SERVICEOVERVIEW, serviceOverview);
								messageSender.convertAndSend("/service/" + serviceOverview.getServiceName(), r);
								
							} catch (MessagingException e1) {
								e1.printStackTrace();
							} catch (Exception e1) {
								e1.printStackTrace();
							}	
						}
					}

				};

				JobHandler.addListener(eventListener);

				storageScaleExecutor.execTask(jobList.toArray(new Job[] {}));

				log.info(serviceName + " 서비스 셧다운 요청.");
				result = new Result(txId, IResult.RUNNING, stsName + "<br>서비스를 셧다운 합니다.");
			} else {
				result = new Result(txId, IResult.ERROR, "서비스 셧다운 실행 오류.");
			}
		} catch (KubernetesClientException e) {
			log.error(e.getMessage(), e);

			if (e.getMessage().indexOf("Unauthorized") > -1) {
				return new Result(txId, Result.UNAUTHORIZED, "클러스터에 접근이 불가하거나 인증에 실패 했습니다.", null);
			} else {
				return new Result(txId, Result.UNAUTHORIZED, e.getMessage(), e);
			}
		} catch (Exception e) {
			log.error(e.getMessage(), e);
			return Result.RESULT_FAIL(txId, e);
		} finally {
		}
		
		return result;
	
	}
	
	@Override
	public Result serviceChaneMasterToSlave(String txId, String namespace, String serviceType, String serviceName) throws Exception {
		Result result = null;
		try(DefaultKubernetesClient client = K8SUtil.kubernetesClient()) {
			
//		    Result : 
//			*************************** 1. row ***************************
//			Variable_name: read_only
//			        Value: OFF
			StringBuffer history = new StringBuffer();
			
			StringBuffer sqlString = new StringBuffer();
			sqlString.append("stop slave;");
			sqlString.append("set global read_only=0;flush privileges;");
			sqlString.append("show variables like 'read_only'\\G");
			
			String sql = " mysql -uroot -p$MARIADB_ROOT_PASSWORD -e \"" +sqlString.toString()+"\"";
			
			Pod pod = k8sService.getPod(namespace, serviceName, "slave");
			
			if( pod != null && PodManager.isReady(pod)) {
				String execQuery = ExecCommandUtil.execCmd(namespace, pod.getMetadata().getName(), sql);
				if(execQuery.indexOf("read_only") > -1) {
					String[] split = execQuery.split("\n");
					for (String str : split) {
						if(str.indexOf("Value:") > -1) {
							str = str.trim();
							
							if(str.endsWith("ON")) {
								log.error(pod.getMetadata().getName() +"의 read_only 속성이 ON 으로 읽기 전용 상태입니다.");
								return new Result(txId, Result.ERROR, pod.getMetadata().getName() +"의 read_only 속성이 ON 으로 읽기 전용 상태입니다.");
							} else if(str.endsWith("OFF")) {
								history.append(pod.getMetadata().getName() +"의 read_only 속성이 OFF 로 설정 되었습니다. 쓰기 가능한 상태로 설정 되었습니다.\n");
								log.info(history.toString());
								break;
							} 
						}
					}
				}
			
			} else {
				log.error("{} 의 slave 가 존재하지 않거나 서비스 가용 상태가 아닙니다.", serviceName);
				return new Result(txId, Result.ERROR, serviceName + "의 slave 가 존재하지 않거나 서비스 가용 상태가 아닙니다.");
			}
			
			
			MixedOperation<io.fabric8.kubernetes.api.model.Service, ServiceList, DoneableService, Resource<io.fabric8.kubernetes.api.model.Service, DoneableService>> services = client.inNamespace(namespace).services();
			
			List<Service> items = services.withLabel("release", serviceName).withLabel("component", "master").list().getItems();
			
			if(items.size() > 0) {
				log.info("서비스 전환 대상 서비스가 {}개 존재합니다.", items.size());
				for (Service service : items) {
					log.info("	- {}", service.getMetadata().getName());
				}
			} else {
				log.error("서비스 전환 대상 서비스가 없습니다.");
				return new Result(txId, Result.ERROR, "서비스 전환 대상 서비스가 없습니다.");
			}
			
			for (Service service : items) {
				RestTemplate rest = K8SUtil.getRestTemplate();
				String idToken = K8SUtil.getToken();
				String masterUrl = K8SUtil.getMasterURL();
				
				HttpHeaders headers = new HttpHeaders();
				headers.set("Accept", MediaType.APPLICATION_JSON_VALUE);
				headers.set("Authorization", "Bearer " + idToken);
				headers.set("Content-Type", "application/json-patch+json");
				
//					{ "spec": { "selector": { "component": "slave", } } }
				
				String data = "[{\"op\":\"replace\",\"path\":\"/spec/selector/component\",\"value\":\"slave\"}]";
			    
				HttpEntity<String> requestEntity = new HttpEntity<>(data, headers);

				String endpoint = masterUrl + "/api/v1/namespaces/{namespace}/services/{name}";
				ResponseEntity<String> response = rest.exchange(endpoint, HttpMethod.PATCH, requestEntity, String.class, namespace, service.getMetadata().getName());
				
				if (response.getStatusCode() == HttpStatus.OK) {
					result = new Result(txId, Result.OK, "서비스 L/B가 Slave 인스턴스로 전환 되었습니다.");
					if (!history.toString().isEmpty()) {
						result.putValue(Result.HISTORY, history.toString() +" 가 master 에서 slave 로 전환 되었습니다.\nmaster 서비스로 연결된 App.은 slave DB에 읽기/쓰기 됩니다.");
					}
					
					List<Service> svcList = K8SUtil.getServices(namespace, serviceName);
					metaDataCollector.save(svcList);
					
					// send websocket
					try {
						ServiceOverview serviceOverview = k8sService.getServiceWithName(namespace, serviceType, serviceName);
						
						Result r = Result.RESULT_OK.putValue(IResult.SERVICEOVERVIEW, serviceOverview);
						messageSender.convertAndSend("/service/" + serviceOverview.getServiceName(), r);
					} catch (MessagingException e1) {
						e1.printStackTrace();
					} catch (Exception e1) {
						e1.printStackTrace();
					}
				} else {
					System.err.println("HttpStatus ; " + response.getStatusCode());
					
					log.error(response.getBody());
					result = new Result(txId, Result.ERROR, "서비스 전환 오류.");
				}
			}
			
			return result;	
		} catch (FileNotFoundException | KubernetesClientException e) {
			log.error(e.getMessage(), e);
			if (e.getMessage().indexOf("Unauthorized") > -1) {
				return new Result(txId, Result.UNAUTHORIZED, "클러스터에 접근이 불가하거나 인증에 실패 했습니다.", null);
			} else {
				return new Result(txId, Result.UNAUTHORIZED, e.getMessage(), e);
			}
		} catch (Exception e) {
			log.error(e.getMessage(), e);
			return new Result(txId, Result.ERROR, e.getMessage(), e);
		}
	}
	
	@Override
	public Result serviceChaneSlaveToMaster(String txId, String namespace, String serviceType, String serviceName) throws Exception {
		Result result = null;
		
		try(DefaultKubernetesClient client = K8SUtil.kubernetesClient()) {
			
			StringBuffer history = new StringBuffer();

			MixedOperation<io.fabric8.kubernetes.api.model.Service, ServiceList, DoneableService, Resource<io.fabric8.kubernetes.api.model.Service, DoneableService>> services = client.inNamespace(namespace).services();
			
			List<Service> items = services.withLabel("release", serviceName).withLabel("component", "master").list().getItems();
			
			if(items.size() > 0) {
				log.info("서비스 전환 대상 서비스가 {}개 존재합니다.", items.size());
				for (Service service : items) {
					log.info("	- {}", service.getMetadata().getName());
				}
			} else {
				log.error("서비스 전환 대상 서비스가 없습니다.");
				return new Result(txId, Result.ERROR, "서비스 전환 대상 서비스가 없습니다.");
			}
			
			for (Service service : items) {
				RestTemplate rest = K8SUtil.getRestTemplate();
				String idToken = K8SUtil.getToken();
				String masterUrl = K8SUtil.getMasterURL();
				
				HttpHeaders headers = new HttpHeaders();
				headers.set("Accept", MediaType.APPLICATION_JSON_VALUE);
				headers.set("Authorization", "Bearer " + idToken);
				headers.set("Content-Type", "application/json-patch+json");
				
//					{ "spec": { "selector": { "component": "slave", } } }
				String data = "[{\"op\":\"replace\",\"path\":\"/spec/selector/component\",\"value\":\"master\"}]";
			    
				HttpEntity<String> requestEntity = new HttpEntity<>(data, headers);

				String name = service.getMetadata().getName();
				String endpoint = masterUrl + "/api/v1/namespaces/{namespace}/services/{name}";
				ResponseEntity<String> response = rest.exchange(endpoint, HttpMethod.PATCH, requestEntity, String.class, namespace, name);
				
				if (response.getStatusCode() == HttpStatus.OK) {
					
//					result = new Result(txId, Result.OK, "서비스 전환 완료(slave->master)");
					result = new Result(txId, Result.OK, "서비스 L/B가 Master 인스턴스로 전환 되었습니다.");
					if (!history.toString().isEmpty()) {
						result.putValue(Result.HISTORY, history.toString() +" 가 slave 에서 master 로 전환 되었습니다.");
					}
					
					List<Service> svcList = K8SUtil.getServices(namespace, serviceName);
					metaDataCollector.save(svcList);
					
					// websocket send.
					try {
						ServiceOverview serviceOverview = k8sService.getServiceWithName(namespace, serviceType, serviceName);
						
						Result r = Result.RESULT_OK.putValue(IResult.SERVICEOVERVIEW, serviceOverview);
						messageSender.convertAndSend("/service/" + serviceOverview.getServiceName(), r);
					} catch (MessagingException e1) {
						e1.printStackTrace();
					} catch (Exception e1) {
						e1.printStackTrace();
					}
				} else {
					System.err.println("HttpStatus ; " + response.getStatusCode());
					
					log.error(response.getBody());
					result = new Result(txId, Result.ERROR, "서비스 전환 오류.");
				}
			}
			
			return result;	
		} catch (FileNotFoundException | KubernetesClientException e) {
			log.error(e.getMessage(), e);
			if (e.getMessage().indexOf("Unauthorized") > -1) {
				return new Result(txId, Result.UNAUTHORIZED, "클러스터에 접근이 불가하거나 인증에 실패 했습니다.", null);
			} else {
				return new Result(txId, Result.UNAUTHORIZED, e.getMessage(), e);
			}
		} catch (Exception e) {
			log.error(e.getMessage(), e);
			return new Result(txId, Result.ERROR, e.getMessage(), e);
		}
	}
	
	public Result slowlogRotation(String txId, String namespace, String serviceType, String serviceName) throws Exception {
		try(DefaultKubernetesClient client = K8SUtil.kubernetesClient()) {
			StringBuffer history = new StringBuffer();
			
			Pod pod = k8sService.getPod(namespace, serviceName, "master");
			
			if( pod != null && PodManager.isReady(pod)) {
				{ // slow_query_log 설정 off & 
					StringBuffer sqlString = new StringBuffer();
					sqlString.append("set global slow_query_log=0;flush slow logs;");
					
					String sql = "/opt/bitnami/mariadb/bin/mysql -uroot -p$MARIADB_ROOT_PASSWORD -e \"" +sqlString.toString()+"\"";
					ExecCommandUtil.execCmd(namespace, pod.getMetadata().getName(), sql);
				}
				
//				ExecCommandUtil.execCmd(namespace, pod.getMetadata().getName(), "[ -f /bitnami/mariadb/logs/maria_slow-$(date +%Y-%m-%d)3.log ] && echo "aaa" || echo "bbb"");
				ExecCommandUtil.execCmd(namespace, pod.getMetadata().getName(), "mv /bitnami/mariadb/logs/maria_slow.log /bitnami/mariadb/logs/maria_slow-$(date +%Y-%m-%d).log");
				
				{ // slow_query_log 설정 on & 
					StringBuffer sqlString = new StringBuffer();
					sqlString.append("set global slow_query_log=on;");
					
					String sql = "/opt/bitnami/mariadb/bin/mysql -uroot -p$MARIADB_ROOT_PASSWORD -e \"" +sqlString.toString()+"\"";
					ExecCommandUtil.execCmd(namespace, pod.getMetadata().getName(), sql);
				}
			
				String result = ExecCommandUtil.execCmd(namespace, pod.getMetadata().getName(), "cd /bitnami/mariadb/logs/;ls -al maria_slow*");
				
				String[] split = result.split("\n");
				for (String str : split) {
					history.append(str).append("\n");
					
					log.info(str);
				}
				
			} else {
				log.error("{} 의 slave 가 존재하지 않거나 서비스 가용 상태가 아닙니다.", serviceName);
				return new Result(txId, Result.ERROR, serviceName + "의 master 가 존재하지 않거나 서비스 가용 상태가 아닙니다.");
			}
			
			Result result = new Result(txId, Result.OK, "Slowlog rotation 완료");
			if (!history.toString().isEmpty()) {
				result.putValue(Result.HISTORY, history.toString());
			}
			
			return result;		
		} catch (FileNotFoundException | KubernetesClientException e) {
			log.error(e.getMessage(), e);
			if (e.getMessage().indexOf("Unauthorized") > -1) {
				return new Result(txId, Result.UNAUTHORIZED, "클러스터에 접근이 불가하거나 인증에 실패 했습니다.", null);
			} else {
				return new Result(txId, Result.UNAUTHORIZED, e.getMessage(), e);
			}
		} catch (Exception e) {
			log.error(e.getMessage(), e);
			return new Result(txId, Result.ERROR, e.getMessage(), e);
		}
	}
	

	
	/* (non-Javadoc)
	 * Master 장애로 서비스LB 를 Master -> Slave 로 전환 여부를 반환한다.
	 * 
	 * Result.message 로 상태값 반환
	 *  - MasterToSlave
	 *  - MasterToMaster
	 *  - unknown
	 *  
	 *  
	 * @see com.zdb.core.service.AbstractServiceImpl#serviceFailOverStatus(java.lang.String, java.lang.String, java.lang.String, java.lang.String)
	 */
	@Override
	public Result serviceFailOverStatus(String txId, String namespace, String serviceType, String serviceName) throws Exception {
		try {
			String status = k8sService.getServiceFailOverStatus(namespace, serviceType, serviceName);
			if("unknown".equals(status)) {
				return new Result(txId, Result.ERROR, "unknown");
			} else {
				Result result = new Result(txId, Result.OK);
				result.putValue("status", status);
				return result;
			}

		} catch (FileNotFoundException | KubernetesClientException e) {
			log.error(e.getMessage(), e);
			if (e.getMessage().indexOf("Unauthorized") > -1) {
				return new Result(txId, Result.UNAUTHORIZED, "클러스터에 접근이 불가하거나 인증에 실패 했습니다.", null);
			} else {
				return new Result(txId, Result.UNAUTHORIZED, e.getMessage(), e);
			}
		} catch (Exception e) {
			log.error(e.getMessage(), e);
			return new Result(txId, Result.ERROR, e.getMessage(), e);
		}
	}

	/**
	 * Auto Failover 
	 *  - On : add label : zdb-failover-enable=true
	 *        cli : kubectl -n <namespace> label sts <sts_name> "zdb-failover-enable=true" --overwrite
	 *  - Off : update label : zdb-failover-enable=false
	 *        cli : kubectl -n <namespace> label sts <sts_name> "zdb-failover-enable=false" --overwrite
	 *        
	 * @param txId
	 * @param namespace
	 * @param serviceType
	 * @param serviceName
	 * @return
	 * @throws Exception
	 */
	public Result updateAutoFailoverEnable(String txId, String namespace, String serviceType, String serviceName, boolean enable) throws Exception {

		Result result = new Result(txId);

		String historyValue = "";
		
		try(DefaultKubernetesClient client = K8SUtil.kubernetesClient();) {
			// 서비스 명 체크
			ReleaseMetaData releaseMetaData = releaseRepository.findByReleaseName(serviceName);
			if( releaseMetaData == null) {
				String msg = "서비스가 존재하지 않습니다.";
				return new Result(txId, IResult.ERROR, msg);
			}

			if(enable) {
				// etcd & zdb-ha-manager 가 ready 상태인지 체크
				
				{
					boolean etcdStatus = false;
					List<Pod> items = client.inNamespace("zdb-system").pods().withLabel("app","etcd").list().getItems();
					
					for (Pod pod : items) {
						if(PodManager.isReady(pod)) {
							etcdStatus = true;
							break;
						}
					}
					if(!etcdStatus) {
						result = new Result(txId, Result.ERROR , "Auto-Failover 를 사용 할 수 없습니다.<br>원인: etcd 사용 불가");
						return result;
					}
				}
				
				{
					boolean haManagerStatus = false;
					List<Pod> items = client.inNamespace("zdb-system").pods().withLabel("app","zdb-ha-manager").list().getItems();
					
					for (Pod pod : items) {
						if(PodManager.isReady(pod)) {
							haManagerStatus = true;
							break;
						}
					}
					if(!haManagerStatus) {
						result = new Result(txId, Result.ERROR , "Auto-Failover 를 사용 할 수 없습니다.<br>원인: zdb-ha-manager 사용 불가");
						return result;
					}
				}
				
			}
			
			MixedOperation<StatefulSet, StatefulSetList, DoneableStatefulSet, RollableScalableResource<StatefulSet, DoneableStatefulSet>> statefulSets = 
					client.inNamespace(namespace).apps().statefulSets();

			List<StatefulSet> stsList = statefulSets.withLabel("app", "mariadb").withLabel("release", serviceName).list().getItems();
			if(stsList == null || stsList.size() < 2) {
				result = new Result(txId, Result.ERROR , "Master/Slave 로 구성된 서비스에서만 설정 가능합니다. ["+namespace +" > "+ serviceName +"]");
				return result;
			}
			
			List<StatefulSet> items = statefulSets
					.withLabel("app", "mariadb")
					.withLabel("component", "master")
					.withLabel("release", serviceName)
					.list().getItems();
			
			
			if(items != null && !items.isEmpty()) {
				StatefulSet sts = items.get(0);
				
				List<Job> jobList = new ArrayList<>();
				JobParameter param = new JobParameter();
				param.setNamespace(namespace);
				param.setServiceType(serviceType);
				param.setServiceName(serviceName);
				param.setStatefulsetName(sts.getMetadata().getName());
				param.setToggle(enable ? 1 : 0);
				
				EnableAutofailover job1 = new EnableAutofailover(param);
				
				jobList.add(job1);
				
				if (jobList.size() > 0) {
					CountDownLatch latch = new CountDownLatch(jobList.size());
					
					JobExecutor storageScaleExecutor = new JobExecutor(latch);
					
					final String _historyValue = String.format("Auto-Failover 설정 변경(%s)", serviceName);
					
					EventListener eventListener = new EventListener() {
						
						@Override
						public void onEvent(Job job, String event) {
							log.info(job.getJobName() + "  onEvent - " + event);
						}
						
						@Override
						public void status(Job job, String status) {
							//log.info(job.getJobName() + " : " + status);
						}
						
						@Override
						public void done(Job job, JobResult code, String message, Throwable e) {
							if (jobList.contains(job)) {
								RequestEvent event = new RequestEvent();
								
								event.setTxId(txId);
								event.setStartTime(new Date(System.currentTimeMillis()));
								event.setServiceType(serviceType);
								event.setNamespace(namespace);
								event.setServiceName(serviceName);
								event.setOperation(RequestEvent.SET_AUTO_FAILOVER_USABLE);
								event.setUserId("SYSTEM");
								try {
									if (code == JobResult.ERROR) {
										event.setStatus(IResult.ERROR);
										event.setResultMessage(job.getJobName() + " 처리 중 오류가 발생했습니다. (" + e.getMessage() + ")");
									} else {
										
										List<StatefulSet> statefulSets = K8SUtil.getStatefulSets(namespace, serviceName);
										metaDataCollector.save(statefulSets);
										
										event.setStatus(IResult.OK);
										if (message == null || message.isEmpty()) {
											event.setResultMessage(job.getJobName() + " 정상 처리되었습니다.");
										} else {
											event.setResultMessage(message);
										}
									}
									event.setHistory(_historyValue);
									event.setEndTime(new Date(System.currentTimeMillis()));
									ZDBRepositoryUtil.saveRequestEvent(zdbRepository, event);
									JobHandler.removeListener(this);
									
									ServiceOverview serviceOverview = k8sService.getServiceWithName(namespace, serviceType, serviceName);
									
									Result r = Result.RESULT_OK.putValue(IResult.SERVICEOVERVIEW, serviceOverview);
									messageSender.convertAndSend("/service/" + serviceOverview.getServiceName(), r);
									
								} catch (MessagingException e1) {
									e1.printStackTrace();
								} catch (Exception e1) {
									e1.printStackTrace();
								}	
							}
						}
						
					};
					
					JobHandler.addListener(eventListener);
					
					storageScaleExecutor.execTask(jobList.toArray(new Job[] {}));
					
					log.info(serviceName + " Auto Failover 설정.");
					if(enable) {
						result = new Result(txId, IResult.RUNNING, "서비스 : " + serviceName + "<br>Auto Failover 기능을 적용합니다.<br>처음 적용시 서비스가 재시작 됩니다.");
					} else {
						result = new Result(txId, IResult.RUNNING, "서비스 : " + serviceName + "<br>Auto Failover 기능을 해제 합니다.");
					}
				} else {
					result = new Result(txId, IResult.ERROR, "Auto Failover 설정 오류.");
				}
				
			}
			
		} catch (KubernetesClientException e) {
			log.error(e.getMessage(), e);

			if (e.getMessage().indexOf("Unauthorized") > -1) {
				return new Result(txId, Result.UNAUTHORIZED, "클러스터에 접근이 불가하거나 인증에 실패 했습니다.", null);
			} else {
				return new Result(txId, Result.UNAUTHORIZED, e.getMessage(), e);
			}
		} catch (Exception e) {
			log.error(e.getMessage(), e);
			return Result.RESULT_FAIL(txId, e);
		} finally {
		}
		
		return result;
	
	}
	
	@Override
	public Result getAutoFailoverServices(String txId, String namespace) throws Exception {
		return getAutoFailoverService(txId, namespace, null);
	}
	
	public Result getAutoFailoverService(String txId, String namespace, String serviceName) throws Exception {
		Result result = new Result(txId);
		List<StatefulSet> services = k8sService.getAutoFailoverServices(namespace, serviceName);

		if(services != null && !services.isEmpty()) {
			String[] array = new String[services.size()];
			
			for (int i = 0; i < services.size(); i++) {
				StatefulSet sts = services.get(i);
				array[i] = sts.getMetadata().getName();
			}
			
			result.putValue("services", array);
			result.setCode(Result.OK);
		} else {
			result.setCode(Result.OK);
			result.setMessage("[]");
		}

		return result;
	}
	
	/**
	 * Master/Slave 로 구성된 인스턴스
	 * StatefulSet master 에 edit
	 * 	- report_status.sh 을 실행 할 수 있도록 configmap 등록
	 * 	- spec>template>spec>containers>lifecycle : shell command 등록
	 * 	- spec>template>spec>containers>volumeMounts  :  report-status 추가 
	 * 	- spec>template>spec>volumes :  report-status 추가
	 * 	- label 추가 (zdb-failover-enable=true)
	 * 
	 */
//	public Result addAutoFailover(DefaultKubernetesClient client, StatefulSet sts) throws Exception {
//		
//		
//		Result result = null;
//		StringBuffer resultMsg = new StringBuffer();
//		boolean status = false;
//		
//		try{
//			// * - report_status.sh 을 실행 할 수 있도록 configmap 등록
//			String namespace = sts.getMetadata().getNamespace();
//			String stsName = sts.getMetadata().getName();
//			
//			MixedOperation<ConfigMap, ConfigMapList, DoneableConfigMap, Resource<ConfigMap, DoneableConfigMap>> configMaps = client.inNamespace(namespace).configMaps();
//
//			String cmName = "report-status";
//			ConfigMap configMap = configMaps.withName(cmName).get();
//
//			if (configMap == null) {
//				log.info("Create configmap : " + namespace +" > "+ cmName);
//				
//				InputStream is = new ClassPathResource("mariadb/report_status.template").getInputStream();
//
//				String temp = IOUtils.toString(is, StandardCharsets.UTF_8.name());
//
//				Map<String, String> data = new HashMap<>();
//				data.put("report_status.sh", temp);
//
//				Resource<ConfigMap, DoneableConfigMap> configMapResource = client.configMaps().inNamespace(namespace).withName(cmName);
//
//				configMap = configMapResource.createOrReplace(new ConfigMapBuilder().withNewMetadata().withName(cmName).endMetadata().addToData(data).build());
//			
//				log.info("Created configmap : " + namespace +" > "+ cmName);
//				
//				status = true;
//			} else {
//				log.info("Exist configmap : " + namespace +" > "+ configMap.getMetadata().getName());
//			}
//
////			 * 	- spec>template>spec>containers>lifecycle : shell command 등록
////			 * 	- spec>template>spec>containers>volumeMounts  :  report-status 추가 
////			 * 	- spec>template>spec>volumes :  report-status 추가
////			 * 	- label 추가 (zdb-failover-enable=true)
//			MixedOperation<StatefulSet, StatefulSetList, DoneableStatefulSet, RollableScalableResource<StatefulSet, DoneableStatefulSet>> statefulSets = 
//					client.inNamespace(namespace).apps().statefulSets();
//			
//			{				
////				Map<String, String> labels = sts.getMetadata().getLabels();
//				
//				log.info("Start update statefulset. " + namespace +" > "+ stsName);
//				
//				io.fabric8.kubernetes.api.model.PodSpec spec = sts.getSpec().getTemplate().getSpec();
//				List<Container> containers = spec.getContainers();
//				int mariadbIndex = -1;
//				boolean existVolumeMount = false;
//				boolean existVolume = false;
//				boolean existCommand = false;
//				
//				for (int i = 0; i < containers.size(); i++) {
//					Container container = containers.get(i);
//					
//					String name = container.getName();
//					if("mariadb".equals(name)) {
//						mariadbIndex = i;
//						
//						List<VolumeMount> volumeMounts = container.getVolumeMounts();
//						for (VolumeMount vm : volumeMounts) {
//							if("report-status".equals(vm.getName())) {
//								existVolumeMount = true;
//								break;
//							}
//						}
//						
//						try {
//							int size = container.getLifecycle().getPostStart().getExec().getCommand().size();
//							existCommand = size > 0 ? true : false;
//						} catch (Exception e) {
//							existCommand = false;
//						}
//						
//						break;
//					}
//				}
//				
//				if(mariadbIndex == -1) {
//					String msg = "mariadb container 가 존재하지 않습니다. ["+namespace+" > "+stsName+"]";
//					log.error(msg);
//					
//					result = new Result("", Result.ERROR, msg);
//					return result;
//				}
//
//				List<Volume> volumes = spec.getVolumes();
//				for (Volume v : volumes) {
//					if("report-status".equals(v.getName())) {
//						existVolume = true;
//						break;
//					}
//				}
//				
//				StatefulSetBuilder builder = new StatefulSetBuilder(sts);
//
//				boolean buildFlag = false;
//				
////				String labelKey = "zdb-failover-enable";
////				if(!labels.containsKey(labelKey) || !"true".equals(labels.get(labelKey))) {
////					labels.put(labelKey, "true");
////					
////					builder.editMetadata().withLabels(labels).endMetadata();
////					buildFlag = true;
////					
////				}
////				
////				log.info("withLabels : " + stsName);
//				
//				if(!existCommand) {
//					String[] command = new String[] {
//							"/bin/sh",
//							"-c",
//							"/usr/bin/nohup /report_status.sh 1>/tmp/report.log 2>/dev/null &"
//					};
//					
//					builder.editSpec()
//					.editTemplate().editSpec()
//					.editContainer(mariadbIndex)
//					.editOrNewLifecycle()
//					.editOrNewPostStart()
//					.editOrNewExec()
//					.addToCommand(command)
//					.endExec()
//					.endPostStart()
//					.endLifecycle()
//					.endContainer()
//					.endSpec()
//					.endTemplate()
//					.endSpec();
//
//					buildFlag = true;
//					log.info("addToCommand : " + stsName);
//				} else {
//					log.info("existCommand : " + namespace +" > "+ stsName);
//				}
//				
//				if(!existVolumeMount) {
//					VolumeMount vm = new VolumeMount();
//					vm.setMountPath("/report_status.sh");
//					vm.setName("report-status");
//					vm.setSubPath("report_status.sh");
//					
//					builder
//					.editSpec()
//					.editTemplate()
//					.editSpec()
//					.editContainer(mariadbIndex)
//					.addToVolumeMounts(vm)
//					.endContainer()
//					.endSpec()
//					.endTemplate()
//					.endSpec();
//					
//					buildFlag = true;
//					log.info("addToVolumeMounts : " + stsName);
//				} else {
//					log.info("existVolumeMount : " + namespace +" > "+ stsName);
//				}
//				
//				if(!existVolume) {
//					Volume volume = new Volume();
//					
//					ConfigMapVolumeSource cmvs = new ConfigMapVolumeSource();
//					cmvs.setDefaultMode(493); // 0755
//					cmvs.setName("report-status");
//					volume.setConfigMap(cmvs);
//					volume.setName("report-status");
//					
//					builder
//					.editSpec()
//					.editTemplate()
//					.editSpec()
//					.addToVolumes(volume)
//					.endSpec()
//					.endTemplate()
//					.endSpec();
//					
//					buildFlag = true;
//					log.info("addToVolumes : " + stsName);
//				} else {
//					log.info("existVolume : " + namespace +" > "+ stsName);
//				}
//
//				if (buildFlag) {
//					status = true;
//					StatefulSet newSvc = builder.build();
//
//					statefulSets.createOrReplace(newSvc);
//					log.info("End statefulset update. " + namespace +" > "+ stsName);
//					resultMsg.append("Update statefulset. " + namespace +" > "+ stsName).append("\n");
//				} else {
//					log.info("Skip update statefulset. " + namespace +" > "+ stsName);
//				}
//				
//			}
//			
//			if(!status) {
//				resultMsg.append("이미 설정된 서비스 입니다. " + namespace +" > "+ stsName);
//			}
//					
//			result = new Result("", Result.OK , "Auto Failover 설정 등록 완료.");
//			result.putValue(Result.HISTORY, resultMsg.toString());
//		} catch (Exception e) {
//			log.error(e.getMessage(), e);
//			result = new Result("", Result.ERROR , resultMsg.toString(), e);
//		} 
//		return result;
//	
//	}
	

	public Result changePort(String txId, String namespace, String serviceName, String port) {
		try(DefaultKubernetesClient client = K8SUtil.kubernetesClient()) {
			StringBuffer history = new StringBuffer();
			
			history.append("포트 변경 : xxxx > " + port);
			
			Result result = new Result(txId, Result.OK, "포트 변경 완료");
			if (!history.toString().isEmpty()) {
				result.putValue(Result.HISTORY, history.toString());
			}
			
			return result;		
		} catch (FileNotFoundException | KubernetesClientException e) {
			log.error(e.getMessage(), e);
			if (e.getMessage().indexOf("Unauthorized") > -1) {
				return new Result(txId, Result.UNAUTHORIZED, "포트 변경중 오류가 발생했습니다.", null);
			} else {
				return new Result(txId, Result.UNAUTHORIZED, e.getMessage(), e);
			}
		} catch (Exception e) {
			log.error(e.getMessage(), e);
			return new Result(txId, Result.ERROR, e.getMessage(), e);
		}
	}
	
	
	
}
