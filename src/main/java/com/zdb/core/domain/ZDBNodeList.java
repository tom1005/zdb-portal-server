package com.zdb.core.domain;

import java.util.List;

import lombok.Data;

@Data
public class ZDBNodeList {
    private String msg;
    private String code;
    private ZdbNode data;
    
    public class ZdbNode {
        List<ZDBNode> items;

        public List<ZDBNode> getItems() {
            return items;
        }

        public void setItems(List<ZDBNode> items) {
            this.items = items;
        }
    }
}
