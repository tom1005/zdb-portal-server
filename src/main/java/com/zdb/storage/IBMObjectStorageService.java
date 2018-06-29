package com.zdb.storage;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

import com.ibm.cloud.objectstorage.ClientConfiguration;
import com.ibm.cloud.objectstorage.auth.AWSCredentials;
import com.ibm.cloud.objectstorage.auth.AWSStaticCredentialsProvider;
import com.ibm.cloud.objectstorage.client.builder.AwsClientBuilder.EndpointConfiguration;
import com.ibm.cloud.objectstorage.oauth.BasicIBMOAuthCredentials;
import com.ibm.cloud.objectstorage.services.s3.AmazonS3;
import com.ibm.cloud.objectstorage.services.s3.AmazonS3ClientBuilder;
import com.ibm.cloud.objectstorage.services.s3.model.AmazonS3Exception;
import com.ibm.cloud.objectstorage.services.s3.model.Bucket;
import com.ibm.cloud.objectstorage.services.s3.model.DeleteObjectRequest;
import com.ibm.cloud.objectstorage.services.s3.model.GetObjectRequest;
import com.ibm.cloud.objectstorage.services.s3.model.ListObjectsRequest;
import com.ibm.cloud.objectstorage.services.s3.model.ObjectListing;
import com.ibm.cloud.objectstorage.services.s3.model.PutObjectRequest;
import com.ibm.cloud.objectstorage.services.s3.model.PutObjectResult;
import com.ibm.cloud.objectstorage.services.s3.model.S3Object;
import com.ibm.cloud.objectstorage.services.s3.model.S3ObjectSummary;

import lombok.extern.slf4j.Slf4j;

import com.zdb.storage.StorageConfig;
import com.zdb.storage.StoredObject;

/**
 * 
 * @author Administrator
 *
 */
@Slf4j
public class IBMObjectStorageService implements StorageService {
	private static String COS_AUTH_ENDPOINT = "https://iam.ng.bluemix.net/oidc/token";
	private AmazonS3 _s3Client;
	public IBMObjectStorageService() {
		_s3Client = createIBMClient(StorageConfig.getApiKey(),
				StorageConfig.getEndpointUrl(),
				StorageConfig.getServiceInstanceId(),
				StorageConfig.getLocation()
				);
	}
	
	@Override
	public void close() {
		if (_s3Client != null) {
			_s3Client.shutdown();
			_s3Client = null;
		}
	}
	
	private AmazonS3 createIBMClient(
			String apiKey,
			String endpoint,
			String service_instance_id,
			String location
			) {
		AWSCredentials credentials = new BasicIBMOAuthCredentials(apiKey, service_instance_id);
		ClientConfiguration clientConfig = new ClientConfiguration().withRequestTimeout(0);
		clientConfig.setUseTcpKeepAlive(true);
		AmazonS3 s3Client = AmazonS3ClientBuilder.standard().withCredentials(new AWSStaticCredentialsProvider(credentials))
                .withEndpointConfiguration(new EndpointConfiguration(endpoint, location)).withPathStyleAccessEnabled(true)
                .withClientConfiguration(clientConfig).build();

		return s3Client;
	}

	@Override
	public boolean createBucketNotExist(String bucketName) {
		boolean ret = false;
		
		try {
    		if (false == _s3Client.doesBucketExist(bucketName)) {
    			log.info("createBucketNotExist>>>>>>>>>>>>>>bucket ("+bucketName+") is not found!");
    			Bucket b = _s3Client.createBucket(bucketName);
    			log.info("createBucketNotExist>>>>>>>>>>>>>>bucket ("+b.getName()+") is created!");
	    		_s3Client.notifyAll();
    		} else {
    			log.debug("createBucketNotExist>>>>>>>>>>>>> bucket("+bucketName+") exists.");
    		}
			ret = true;
		} catch (Exception e) {
			ret = false;
			log.error(e.getMessage(), e);
		}
		return ret;
	}
	
	/**
	 * 
	 * @return
	 */
	@Override
    public List<StoredObject> getObjects(String bucketName) {
        ObjectListing objectListing = _s3Client.listObjects(new ListObjectsRequest().withBucketName(bucketName));
        List<StoredObject> list = new ArrayList<StoredObject>();
        for (S3ObjectSummary objSum : objectListing.getObjectSummaries()) {
        	StoredObject obj = new StoredObject();
        	obj.setBucketName(objSum.getBucketName());
        	obj.setKey(objSum.getKey());
        	list.add(obj);
        }
        return list;
    }
    
    /**
     * 
     * @param key
     * @return
     */
	@Override
    public StoredObject findObject(String bucketName, String key) {
    	S3Object obj = _s3Client.getObject(bucketName, key);
    	StoredObject s3obj = new StoredObject();
    	s3obj.setBucketName(obj.getBucketName());
    	s3obj.setKey(obj.getKey());
    	return s3obj;
    }

	@Override
	public boolean hasBucket(String bucketName) {
		boolean res = false;
		try {
			res = _s3Client.doesBucketExist(bucketName);
		} catch (AmazonS3Exception e) {
    		if ("403".equals(e.getErrorCode())) {
    			log.info("buckte("+bucketName+") may not been deleted completely.");
    			res = true;
    		}
    		log.warn(e.getMessage(), e);
		}
		return res;
	}
	
	@Override
    public boolean hasObject(String bucketName, String key) {
    	boolean res = false;
    	try {
    		res = _s3Client.doesObjectExist(bucketName, key);
    	} catch (AmazonS3Exception e) {
    		if ("403".equals(e.getErrorCode())) {
    			log.info("file ("+key+") in buckte("+bucketName+") may not been deleted completely.");
    			res = true;
    		}
    		log.warn(e.getMessage(), e);
    	}
    	return res;
    }
    /**
     * 
     * @param key
     * @param filePath
     */
	public StoredObject uploadObject(String bucketName, String key, String filePath) throws Exception {
    	log.info(">>>>>>>>>> trying to uploadfile : "+filePath);
    	StoredObject obj = null;
		long start = System.currentTimeMillis();
		if (false == hasBucket(bucketName)) {
    		Bucket b = _s3Client.createBucket(bucketName);
    		log.info("upload>>>>>>>>>>>>>>>>>>> bucket ("+b.getName()+") is created!");
		} else {
    		log.info("upload>>>>>>>>>>>>>>>>>>> bucket ("+bucketName+") exists");
		}
		if (!hasObject(bucketName, key)) {
    		File f = new File(filePath);
	    	PutObjectResult res =_s3Client.putObject(new PutObjectRequest(bucketName, key, f));
	    	long elasped = System.currentTimeMillis() - start;
	    	log.info(filePath+" uploaded : "+elasped);
	    	obj = new StoredObject();
	    	obj.setBucketName(bucketName);
	    	obj.setKey(key);
	    	obj.setEndPointUrl(StorageConfig.getEndpointUrl());
	    	obj.setSize(f.length());
		} else {
			log.warn("file("+key+") in bucket("+bucketName+") may not been deleted!");
			throw new Exception("file("+key+") in bucket("+bucketName+") may not been deleted!");
		}
    	return obj;
    }
    
    /**
     * 
     * @param key
     * @param filePath
     */
	@Override
    public boolean deleteObject(String bucketName, String key) {
    	log.info(">>>>>>>>>> trying to remove : bucketName : "+bucketName
    			+", key : "+key);
		long start = System.currentTimeMillis();
		_s3Client.deleteObject(new DeleteObjectRequest(bucketName, key));
    	long elasped = System.currentTimeMillis() - start;
    	log.info(key+" removed : "+elasped);
    	return true;
    }
    
	@Override
    public boolean deleteBucket(String bucketName) {
    	boolean result = true;
    	if (_s3Client.doesBucketExist(bucketName)) {
    		_s3Client.deleteBucket(bucketName);
    		log.info(">>>>>>>>>>>>>> bucket("+bucketName+") is deleted");
    	} else {
    		log.info("<<<<<<<<<<<<<< bucket("+bucketName+") does not exist");
    	}
    	return result;
    }
    
    /**
     * 
     * @param key
     * @param filePath
     */
	@Override
    public boolean downloadObject(String bucketName, String key, String filePath) {
    	log.info(">>>>>>>>>> trying to download : "+key+" to "+filePath);
    	boolean ret = false;
    	try {
    		long start = System.currentTimeMillis();
    		_s3Client.getObject(new GetObjectRequest(bucketName, key), new File(filePath));
	    	long elasped = System.currentTimeMillis() - start;
	    	log.info(key+" downloaded to "+filePath+" : "+elasped);
	    	ret = true;
    	} catch(Exception e) {
    		e.printStackTrace();
	    	ret = false;
    	}
    	return ret;
    }
    
	@Override
    public List<String> getBuckets() {
    	List<Bucket> buckets = _s3Client.listBuckets();
    	List<String> list = new ArrayList<String>();
    	for (Bucket b : buckets) {
    		list.add(b.getName());
    	}
    	return list;
    }
}
