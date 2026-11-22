package io.github.shigureyukina.octobus.domain;

import io.github.shigureyukina.octobus.common.ApiException;
import io.github.shigureyukina.octobus.persistence.GroupRepository;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

@Service
public class GroupService {

    private final GroupRepository groupRepository;

    public GroupService(GroupRepository groupRepository) {
        this.groupRepository = groupRepository;
    }

    public List<Group> listGroups() {
        return groupRepository.findAll();
    }

    public Group createGroup(GroupCreateRequest request) {
        return groupRepository.create(request);
    }

    public Group updateGroup(GroupUpdateRequest request) {
        groupRepository.findById(request.id())
            .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Group not found"));
        return groupRepository.update(request.id(), request);
    }

    public void deleteGroup(int id) {
        groupRepository.findById(id)
            .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Group not found"));
        groupRepository.delete(id);
    }
}
