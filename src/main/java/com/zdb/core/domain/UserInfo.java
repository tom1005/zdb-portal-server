package com.zdb.core.domain;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@NoArgsConstructor
@AllArgsConstructor
@Data
public class UserInfo {
	String userId;
	String userName;
	String email;
	String accessRole;
	String namespaces;
	String defaultNamespace;
}
