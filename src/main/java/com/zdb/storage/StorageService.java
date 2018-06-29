package com.zdb.storage;

import java.util.List;

import com.zdb.storage.StoredObject;

public interface StorageService {
	public void close();

	public boolean createBucketNotExist(String bucketName);
	
    public List<StoredObject> getObjects(String bucketName);
    
    public StoredObject findObject(String bucketName, String key);

	public boolean hasBucket(String bucketName);
	
    public boolean hasObject(String bucketName, String key);
	
	public StoredObject uploadObject(String bucketName, String key, String filePath) throws Exception;
    
    public boolean deleteObject(String bucketName, String key);
    
    public boolean deleteBucket(String bucketName);
    
    public boolean downloadObject(String bucketName, String key, String filePath);
    
    public List<String> getBuckets();
}
