package com.zhengte.platform.controller;

import com.zhengte.platform.dto.Views;
import com.zhengte.platform.mapper.ProjectMapper;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/projects")
public class ProjectController {

    private final ProjectMapper projectMapper;

    public ProjectController(ProjectMapper projectMapper) {
        this.projectMapper = projectMapper;
    }

    @GetMapping
    public List<Views.Project> list() {
        return projectMapper.findAll().stream()
                .map(p -> new Views.Project(p.id(), p.name(), p.service(), p.repoUrl()))
                .toList();
    }
}
