package com.zdb.storage;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

import com.ibm.cloud.objectstorage.ClientConfiguration;
import com.ibm.cloud.objectstorage.auth.AWSCredentials;
import com.ibm.cloud.objectstorage.auth.AWSStaticCredentialsProvider;
import com.ibm.cloud.objectstorage.auth.BasicAWSCredentials;
import com.ibm.cloud.objectstorage.client.builder.AwsClientBuilder.EndpointConfiguration;
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

import com.zdb.storage.StorageConfig;

import lombok.extern.slf4j.Slf4j;

/**
 * 
 * @author Administrator
 *
 */
@Slf4j
public class S3ObjectStorageService implements StorageService {
	private AmazonS3 _s3Client;

	public S3ObjectStorageService() {
		_s3Client = createClient(StorageConfig.getAccessKey(),
				StorageConfig.getsecretKey(),
				StorageConfig.getEndpointUrl(),
				StorageConfig.getLocation()
				);
	}
	
	public void close() {
		if (_s3Client != null) {
			_s3Client.shutdown();
			_s3Client = null;
		}
	}
	/**
	 * 
	 * @param accessKey
	 * @param secretKey
	 * @param endpoint
	 * @param bucketName
	 * @param location
	 * @return
	 */
	private AmazonS3 createClient(
				String accessKey
				, String secretKey
				, String endpoint
				, String location) {
		
		AWSCredentials credentials = null;

		credentials = new BasicAWSCredentials(accessKey, secretKey);
		if (log.isDebugEnabled()) {
			log.debug("==========> createClient {accessKey : "+accessKey
						+ ", secretKey : "+secretKey
						+ ", endpoint : "+endpoint
						+ ", location : "+location+"}");
		}
        ClientConfiguration clientConfig = new ClientConfiguration().withRequestTimeout(0);
        clientConfig.setUseTcpKeepAlive(true);
        AmazonS3 s3Client = AmazonS3ClientBuilder.standard().withCredentials(new AWSStaticCredentialsProvider(credentials))
                .withEndpointConfiguration(new EndpointConfiguration(endpoint, location)).withPathStyleAccessEnabled(true)
                .withClientConfiguration(clientConfig).build();
		
        return s3Client;
	}

	public boolean createBucketNotExist(String bucketName) {
		boolean ret = false;
		
		try {
    		if (false == _s3Client.doesBucketExist(bucketName)) {
    			if (log.isDebugEnabled()) {
    				log.debug("createBucketNotExist>>>>>>>>>>>>>>bucket ("+bucketName+") is not found!");
    			}
    			Bucket b = _s3Client.createBucket(bucketName);
    			if (log.isDebugEnabled()) {
        			log.info("createBucketNotExist>>>>>>>>>>>>>>bucket ("+b.getName()+") is created!");
    			}
	    		_s3Client.notifyAll();
    		} else {
    			if (log.isInfoEnabled()) {
    				log.debug("createBucketNotExist>>>>>>>>>>>>> bucket("+bucketName+") exists.");
    			}
    		}
			ret = true;
		} catch (Exception e) {
			ret = false;
			if (log.isErrorEnabled()) {
				log.error("Failed to createBucketNotExist {bucketName:"+bucketName+"}", e);
			}
		}
		return ret;
	}
	
	/**
	 * 
	 * @return
	 */
    public List<StoredObject> getObjects(String bucketName) {
        ObjectListing objectListing = _s3Client.listObjects(new ListObjectsRequest().withBucketName(bucketName));
        List<StoredObject> list = new ArrayList<StoredObject>();
        for (S3ObjectSummary objSum : objectListing.getObjectSummaries()) {
        	StoredObject obj = new StoredObject();
        	obj.setBucketName(objSum.getBucketName());
        	obj.setKey(objSum.getKey());
        	obj.setEndPointUrl(StorageConfig.getEndpointUrl());
        	list.add(obj);
        }
    	if (log.isDebugEnabled()) {
    		log.debug("Object count : "+list.size()+" in "+bucketName);
    	}
        return list;
    }
    
    /**
     * 
     * @param key
     * @return
     */
    public StoredObject findObject(String bucketName, String key) {
    	S3Object obj = _s3Client.getObject(bucketName, key);
    	
    	StoredObject s3obj = new StoredObject();
    	s3obj.setBucketName(obj.getBucketName());
    	s3obj.setKey(obj.getKey());
    	s3obj.setEndPointUrl(StorageConfig.getEndpointUrl());
    	
    	return s3obj;
    }

	public boolean hasBucket(String bucketName) {
		boolean res = false;
		try {
			res = _s3Client.doesBucketExist(bucketName);
		} catch (AmazonS3Exception e) {
    		if ("403".equals(e.getErrorCode())) {
    			if (log.isWarnEnabled()) {
    				log.warn("buckte("+bucketName+") may not been deleted completely.");
    			}
    			res = true;
    		} else {
    			if (log.isErrorEnabled()) {
    				log.error("Failed to hasBucket {bucketName:"+bucketName+"} cause of "+e.getClass().getSimpleName(), e);
    			}
    		}
		}
		return res;
	}
	
    public boolean hasObject(String bucketName, String key) {
    	boolean res = false;
    	try {
    		res = _s3Client.doesObjectExist(bucketName, key);
    	} catch (AmazonS3Exception e) {
    		if ("403".equals(e.getErrorCode())) {
    			if (log.isWarnEnabled()) {
    				log.warn("file ("+key+") in buckte("+bucketName+") may not been deleted completely.");
    			}
    			res = true;
    		} else {
    			if (log.isErrorEnabled()) {
    				log.error("Failed to hasBucket {bucketName:"+bucketName
    						+"key : "+key
    						+"} cause of "+e.getClass().getSimpleName(), e);
    			}
    		}
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
		if (false == hasBucket(bucketName)) {
    		Bucket b = _s3Client.createBucket(bucketName);
    		if (log.isInfoEnabled()) {
    			log.info("upload==============> bucket ("+b.getName()+") is created!");
    		}
		} else {
    		if (log.isDebugEnabled()) {
        		log.debug("upload==============> bucket ("+bucketName+") exists");
    		}
		}
		if (!hasObject(bucketName, key)) {
    		File f = new File(filePath);
	    	PutObjectResult res =_s3Client.putObject(new PutObjectRequest(bucketName, key, f));
	    	obj = new StoredObject();
	    	obj.setBucketName(bucketName);
	    	obj.setKey(key);
	    	obj.setEndPointUrl(StorageConfig.getEndpointUrl());
	    	obj.setSize(f.length());
	    	if (log.isDebugEnabled()) {
	    		log.debug(key+" in "+bucketName+" uploaded {"+res.toString()+"}");
	    	}
		} else {
			if (log.isWarnEnabled()) {
				log.warn("Failed to uploade file{filePath:"+filePath
						+",bucketName:"+bucketName
						+",key:"+key+"}");
			}
			throw new Exception("Failed to uploade file{filePath:"+filePath
					+",bucketName:"+bucketName
					+",key:"+key+"}");
		}
    	return obj;
    }
    
    /**
     * 
     * @param key
     * @param filePath
     */
    public boolean deleteObject(String bucketName, String key) {
		boolean result = false;
		int timeOutCount=0;
    	log.info(">>>>>>>>>> trying to remove : bucketName : "+bucketName
    			+", key : "+key);
		_s3Client.deleteObject(new DeleteObjectRequest(bucketName, key));
		do {
			try {
				Thread.sleep(1000);
				if (!_s3Client.doesObjectExist(bucketName, key)) {
					result = true;
				}
			}  catch (Exception e) {
	    		if ((e instanceof AmazonS3Exception) && 
	    				("403".equals(((AmazonS3Exception)e).getErrorCode()))) {
	    			log.warn("File("+key+" in "+bucketName+") may not yet been deleted completely.");
	    		} else {
	    			if (log.isWarnEnabled()) {
	    				log.error("Failed to deleteObject {bucketName:"+bucketName
	    						+"key : "+key
	    						+"} cause of "+e.getClass().getSimpleName(), e);
	    			}
	    		}
			}
		} while(result==false || ++timeOutCount < 60);
    	return true;
    }
    
    public boolean deleteBucket(String bucketName) {
    	boolean result = true;
    	int timeOutCount = 0;
    	if (_s3Client.doesBucketExist(bucketName)) {
    		_s3Client.deleteBucket(bucketName);
    		do {
    			try {
    				Thread.sleep(1000);
    				if (!_s3Client.doesBucketExist(bucketName)) {
    					result = true;
    				}
    			} catch (Exception e) {
    	    		if ((e instanceof AmazonS3Exception) && 
    	    				("403".equals(((AmazonS3Exception)e).getErrorCode()))) {
    	    			log.warn("bucketName ("+bucketName+") may not yet been deleted completely.");
    	    		} else {
    	    			if (log.isWarnEnabled()) {
    	    				log.error("Failed to deleteObject {bucketName:"+bucketName+"} cause of "+e.getClass().getSimpleName(), e);
    	    			}
    	    		}
    			}
    		} while(result==false || ++timeOutCount < 60);
    		if (log.isInfoEnabled()) {
    			log.info("==============> bucket("+bucketName+") is deleted");
    		}
    	} else {
    		if (log.isInfoEnabled()) {
        		log.info("<<<<<<<<<<<<<< bucket("+bucketName+") does not exist");
    		}
    	}
    	return result;
    }
    
    /**
     * 
     * @param key
     * @param filePath
     */
    public boolean downloadObject(String bucketName, String key, String filePath) {
		if (log.isDebugEnabled()) {
			log.debug("==========> trying to download : "+key+" to "+filePath);
		}
    	boolean ret = false;
    	try {
    		_s3Client.getObject(new GetObjectRequest(bucketName, key), new File(filePath));
    		if (log.isInfoEnabled()) {
    			log.info("=========>"+key+" downloaded to "+filePath);
    		}
	    	ret = true;
    	} catch(Exception e) {
	    	ret = false;
    		if (log.isErrorEnabled()) {
    			log.error("Failed to downloadObject{bucketName:"+bucketName
    					+",key:"+key
    					+",filePath:"+filePath
    					+"} cause of "+e.getClass().getSimpleName(), e);
    		}
    	}
    	return ret;
    }
    
    public List<String> getBuckets() {
    	List<Bucket> buckets = _s3Client.listBuckets();
    	List<String> list = new ArrayList<String>();
    	for (Bucket b : buckets) {
    		list.add(b.getName());
    	}
    	return list;
    }
}
