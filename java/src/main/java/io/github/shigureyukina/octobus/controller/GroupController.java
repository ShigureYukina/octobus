package io.github.shigureyukina.octobus.controller;

import io.github.shigureyukina.octobus.common.ApiResponse;
import io.github.shigureyukina.octobus.domain.Group;
import io.github.shigureyukina.octobus.domain.GroupCreateRequest;
import io.github.shigureyukina.octobus.domain.GroupService;
import io.github.shigureyukina.octobus.domain.GroupUpdateRequest;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/group")
class GroupController {

    private final GroupService groupService;

    GroupController(GroupService groupService) {
        this.groupService = groupService;
    }

    @GetMapping("/list")
    ApiResponse<List<Group>> list() {
        return ApiResponse.success(groupService.listGroups());
    }

    @PostMapping("/create")
    ApiResponse<Group> create(@Valid @RequestBody GroupCreateRequest request) {
        return ApiResponse.success(groupService.createGroup(request));
    }

    @PostMapping("/update")
    ApiResponse<Group> update(@Valid @RequestBody GroupUpdateRequest request) {
        return ApiResponse.success(groupService.updateGroup(request));
    }

    @DeleteMapping("/delete/{id}")
    ApiResponse<String> delete(@PathVariable int id) {
        groupService.deleteGroup(id);
        return ApiResponse.success("group deleted successfully");
    }
}
