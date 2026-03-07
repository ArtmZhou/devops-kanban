package com.devops.kanban.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class RemoteDTO {
    private String name;
    private String fetchUrl;
    private String pushUrl;
    private List<String> branches;
}
