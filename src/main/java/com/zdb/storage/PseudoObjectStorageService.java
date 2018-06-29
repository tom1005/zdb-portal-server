package com.zdb.storage;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.List;

import com.zdb.storage.StoredObject;

import lombok.extern.slf4j.Slf4j;

@Slf4j
public class PseudoObjectStorageService implements StorageService {
	private String basePath;
	public PseudoObjectStorageService() {
		basePath = "/zdb_storage";
	}

	@Override
	public boolean createBucketNotExist(String bucketName) {
		boolean ret = false;
		
		try {
			StringBuilder sb = new StringBuilder();
			sb.append(basePath).append(File.separator).append(bucketName);
			
			File f = new File(sb.toString());
			if (!f.exists()) {
				log.info("Not found "+sb.toString()+", now to be created.");
				f.mkdirs();
			}
			ret = true;
		} catch (Exception e) {
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
		StringBuilder sb = new StringBuilder();
		sb.append(basePath).append(File.separator).append(bucketName);
		File f = new File(sb.toString());
		File[] list = f.listFiles();
		List<StoredObject> lst = new ArrayList<StoredObject>();
		for (File sf : list) {
			StoredObject obj = new StoredObject();
			obj.setBucketName(bucketName);
			obj.setKey(sf.getName());
			obj.setEndPointUrl(sf.getAbsolutePath());
			obj.setSize(sf.length());
			lst.add(obj);
		}
		return lst;
    }
    
    /**
     * 
     * @param key
     * @return
     */
	@Override
    public StoredObject findObject(String bucketName, String key) {
    	StoredObject s3obj = null;
		StringBuilder sb = new StringBuilder();
		sb.append(basePath).append(File.separator).append(bucketName);
		File f = new File(sb.toString());
		File[] list = f.listFiles();
		for (File s : list) {
			if (key.equals(s.getName())) {
		    	s3obj = new StoredObject();
		    	s3obj.setBucketName(bucketName);
		    	s3obj.setEndPointUrl(key);
		    	s3obj.setSize(s.length());
		    	StringBuilder sb1 = new StringBuilder();
		    	sb1.append(basePath).append(File.separator).append(bucketName).append(File.separator).append(key);
		    	s3obj.setEndPointUrl(sb1.toString());
				break;
			}
		}
    	return s3obj;
    }

    /**
     * 
     * @param key
     * @param filePath
     */
	@Override
    public StoredObject uploadObject(String bucketName, String key, String filePath) throws Exception {
    	log.info(">>>>>>>>>> trying to uploadfile : "+filePath);
    	StoredObject obj = new StoredObject();
    	try {
    		if (!createBucketNotExist(bucketName)) {
    			throw new Exception(bucketName+" not created");
    		}
    		
    	    InputStream is = null;
    	    OutputStream os = null;
    	    long fsiz = 0L;
    	    try {
    			StringBuilder sb = new StringBuilder();
    			sb.append(basePath).append(File.separator).append(bucketName).append(File.separator).append(key);
    			File src = new File(filePath);
    			fsiz = src.length();
    	        is = new FileInputStream(src);
    	        os = new FileOutputStream(new File(sb.toString()));
    	        byte[] buffer = new byte[1024];
    	        int length;
    	        while ((length = is.read(buffer)) > 0) {
    	            os.write(buffer, 0, length);
    	        }
    	    } finally {
    	        is.close();
    	        os.close();
    	    }
	    	obj.setBucketName(bucketName);
	    	obj.setEndPointUrl(basePath);
	    	obj.setKey(key);
	    	obj.setSize(fsiz);
    	} catch(Exception e) {
    		e.printStackTrace();
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
		StringBuilder sb = new StringBuilder();
		sb.append(basePath).append(File.separator).append(bucketName).append(File.separator).append(key);
		File f = new File(sb.toString());
		f.delete();
    	return true;
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
			StringBuilder sb = new StringBuilder();
			sb.append(basePath).append(File.separator).append(bucketName).append(File.separator).append(key);
			File src = new File(sb.toString());
			File dest = new File(filePath);
    	    InputStream is = null;
    	    OutputStream os = null;
    	    try {
    	        is = new FileInputStream(src);
    	        os = new FileOutputStream(dest);
    	        byte[] buffer = new byte[1024];
    	        int length;
    	        while ((length = is.read(buffer)) > 0) {
    	            os.write(buffer, 0, length);
    	        }
    	    } finally {
    	        is.close();
    	        os.close();
    	    }
	    	ret = true;
    	} catch(Exception e) {
    		e.printStackTrace();
	    	ret = false;
    	}
    	return ret;
    }

	@Override
	public boolean hasBucket(String bucketName) {
		// TODO Auto-generated method stub
		
		return new File(basePath+File.separator+bucketName).exists();
	}

	@Override
	public List<String> getBuckets() {
		// TODO Auto-generated method stub
		List<String> list = new ArrayList<String>();
		File[] children = new File(basePath).listFiles();
		for(File f : children) {
			list.add(f.getName());
		}
		return list;
	}

	@Override
	public boolean deleteBucket(String bucketName) {
		// TODO Auto-generated method stub
		boolean res = true;
		try {
			File f = new File(basePath+File.separator+bucketName);
			if (f.exists()) {
				res = f.delete();
			}
		} catch (Exception e) {
			e.printStackTrace();
		}
		return res;
	}

	@Override
	public boolean hasObject(String bucketName, String key) {
		// TODO Auto-generated method stub
		StringBuilder sb = new StringBuilder();
		sb.append(basePath).append(File.separator).append(bucketName).append(File.separator).append(key);
		return new File(sb.toString()).exists();
	}

	@Override
	public void close() {
		// TODO Auto-generated method stub
		
	}
}
