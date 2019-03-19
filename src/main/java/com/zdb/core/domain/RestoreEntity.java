package com.zdb.core.domain;
 
import java.util.Date;
 
import javax.persistence.Entity;
import javax.persistence.GeneratedValue;
import javax.persistence.Id;
 
import org.hibernate.annotations.GenericGenerator;
 
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
 
@Entity
@NoArgsConstructor
@AllArgsConstructor
@Data
public class RestoreEntity {
 
    @Id
    @GeneratedValue(generator = "uuid")
    @GenericGenerator(name = "uuid", strategy = "uuid2")
    private String restoreId;
    private Date acceptedDatetime;
    private Date startDatetime;
    private Date completeDatetime;
    private Date targetDatetime;
    private String targetBackupId;
    private long targetTolsn = 0l;
    private String namespace;
    private String serviceName;
    private String serviceType;
    private String status;
    private String reason;
}