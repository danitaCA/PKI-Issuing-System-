package com.pki.ca.dto.response;

import java.util.ArrayList;
import java.util.List;

public class SanEntriesDto {

    private List<String> dns = new ArrayList<>();
    private List<String> ip = new ArrayList<>();
    private List<String> email = new ArrayList<>();
    private List<String> uri = new ArrayList<>();

    public SanEntriesDto() {
    }

    public static SanEntriesDto fromList(List<String> entries) {
        SanEntriesDto dto = new SanEntriesDto();
        if (entries == null) {
            return dto;
        }
        for (String entry : entries) {
            if (entry == null) continue;
            String trimmed = entry.trim();
            if (trimmed.startsWith("DNS:")) {
                dto.getDns().add(trimmed.substring(4));
            } else if (trimmed.startsWith("IP:")) {
                dto.getIp().add(trimmed.substring(3));
            } else if (trimmed.startsWith("EMAIL:")) {
                dto.getEmail().add(trimmed.substring(6));
            } else if (trimmed.startsWith("URI:")) {
                dto.getUri().add(trimmed.substring(4));
            } else {
                // Default to DNS if no prefix is given
                dto.getDns().add(trimmed);
            }
        }
        return dto;
    }

    public List<String> toFormattedList() {
        List<String> list = new ArrayList<>();
        if (dns != null) {
            for (String d : dns) list.add("DNS:" + d);
        }
        if (ip != null) {
            for (String i : ip) list.add("IP:" + i);
        }
        if (email != null) {
            for (String e : email) list.add("EMAIL:" + e);
        }
        if (uri != null) {
            for (String u : uri) list.add("URI:" + u);
        }
        return list;
    }

    public List<String> getDns() {
        return dns;
    }

    public void setDns(List<String> dns) {
        this.dns = dns;
    }

    public List<String> getIp() {
        return ip;
    }

    public void setIp(List<String> ip) {
        this.ip = ip;
    }

    public List<String> getEmail() {
        return email;
    }

    public void setEmail(List<String> email) {
        this.email = email;
    }

    public List<String> getUri() {
        return uri;
    }

    public void setUri(List<String> uri) {
        this.uri = uri;
    }
}
