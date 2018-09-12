package com.zdb.core;

import java.io.ByteArrayOutputStream;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;

import org.apache.commons.compress.archivers.tar.TarArchiveEntry;
import org.apache.commons.compress.archivers.tar.TarArchiveOutputStream;

import com.zdb.core.util.K8SUtil;

import io.fabric8.kubernetes.client.dsl.ExecWatch;

public class FileCopyTest {

	public FileCopyTest() {
		// TODO Auto-generated constructor stub
	}

	public static void main(String[] args) {
		String path = "/bitnami/mariadb/logs";
		InputStream inputStream = null;
		try {
			ExecWatch watch = K8SUtil.kubernetesClient().pods().inNamespace("ns-zdb-02").withName("ns-zdb-02-ns-ha002-mariadb-master-0").inContainer("mariadb")
					.redirectingInput().redirectingError()
					.exec("tar", "xf", "-", "-C", "/tmp/test");
			
			TarArchiveEntry entry = new TarArchiveEntry("/logs2.tar");
			
			inputStream = new FileInputStream("/Users/a06919/logs2.tar");

			try (TarArchiveOutputStream tarOut = new TarArchiveOutputStream(watch.getInput()); 
					ByteArrayOutputStream byteOut = new ByteArrayOutputStream();) {

				System.out.println("1---------------------------");
				byte[] bytes = new byte[409600];
				int count;
				while ((count = inputStream.read(bytes)) > 0) {
					byteOut.write(bytes, 0, count);
				}
				
				System.out.println("2---------------------------");

				entry.setSize(byteOut.size());
				System.out.println("3---------------------------");
				tarOut.putArchiveEntry(entry);
				System.out.println("4---------------------------");
				byteOut.writeTo(tarOut);
				System.out.println("5---------------------------");
				tarOut.closeArchiveEntry();
				System.out.println("6---------------------------");
			}

		} catch (Exception e) {
			// TODO: handle exception
		} finally {
			if(inputStream != null) {
				try {
					inputStream.close();
				} catch (IOException e) {
					e.printStackTrace();
				}
			}
		}

	}

}
