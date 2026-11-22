package io.github.shigureyukina.octobus.helper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.github.shigureyukina.octobus.domain.Channel;
import io.github.shigureyukina.octobus.domain.Group;
import io.github.shigureyukina.octobus.domain.GroupItem;
import io.github.shigureyukina.octobus.persistence.GroupRepository;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ChannelAutoGroupTests {

    @Mock
    private GroupRepository groupRepository;

    @InjectMocks
    private ChannelAutoGroup channelAutoGroup;

    @Test
    void noneModeDoesNothing() {
        var channel = new Channel(1, "test", "openai/chat_completions", true, null, List.of(), "gpt-4,gpt-4-turbo", null, false, false, 0, null, null, null, null, null);
        channelAutoGroup.autoGroup(channel);
        verify(groupRepository, never()).batchAddItems(anyInt(), anyList());
    }

    @Test
    void exactModeMatchesByName() {
        var channel = new Channel(1, "test", "openai/chat_completions", true, null, List.of(), "gpt-4,gpt-4-turbo", null, false, false, 2, null, null, null, null, null);
        var groups = List.of(
            new Group(10, "gpt-4", 0, null, 0, 0, List.of())
        );
        when(groupRepository.findAll()).thenReturn(groups);

        channelAutoGroup.autoGroup(channel);

        verify(groupRepository).batchAddItems(10, List.of(
            new io.github.shigureyukina.octobus.domain.GroupIDAndLLMName(1, "gpt-4")
        ));
    }

    @Test
    void exactModeIgnoresCase() {
        var channel = new Channel(1, "test", "openai/chat_completions", true, null, List.of(), "GPT-4", null, false, false, 2, null, null, null, null, null);
        var groups = List.of(
            new Group(10, "gpt-4", 0, null, 0, 0, List.of())
        );
        when(groupRepository.findAll()).thenReturn(groups);

        channelAutoGroup.autoGroup(channel);

        verify(groupRepository).batchAddItems(10, List.of(
            new io.github.shigureyukina.octobus.domain.GroupIDAndLLMName(1, "GPT-4")
        ));
    }

    @Test
    void fuzzyModeMatchesByContains() {
        var channel = new Channel(1, "test", "openai/chat_completions", true, null, List.of(), "gpt-4-0125,claude-3", null, false, false, 1, null, null, null, null, null);
        var groups = List.of(
            new Group(10, "gpt-4", 0, null, 0, 0, List.of())
        );
        when(groupRepository.findAll()).thenReturn(groups);

        channelAutoGroup.autoGroup(channel);

        verify(groupRepository).batchAddItems(10, List.of(
            new io.github.shigureyukina.octobus.domain.GroupIDAndLLMName(1, "gpt-4-0125")
        ));
    }

    @Test
    void regexModeMatchesByPattern() {
        var channel = new Channel(1, "test", "openai/chat_completions", true, null, List.of(), "gpt-4-0125,claude-3", null, false, false, 3, null, null, null, null, null);
        var groups = List.of(
            new Group(10, "gpt group", 0, "gpt-.*", 0, 0, List.of())
        );
        when(groupRepository.findAll()).thenReturn(groups);

        channelAutoGroup.autoGroup(channel);

        verify(groupRepository).batchAddItems(10, List.of(
            new io.github.shigureyukina.octobus.domain.GroupIDAndLLMName(1, "gpt-4-0125")
        ));
    }

    @Test
    void includesCustomModel() {
        var channel = new Channel(1, "test", "openai/chat_completions", true, null, List.of(), "gpt-4", "gpt-4-turbo", false, false, 2, null, null, null, null, null);
        var groups = List.of(
            new Group(10, "gpt-4", 0, null, 0, 0, List.of()),
            new Group(11, "gpt-4-turbo", 0, null, 0, 0, List.of())
        );
        when(groupRepository.findAll()).thenReturn(groups);

        channelAutoGroup.autoGroup(channel);

        verify(groupRepository).batchAddItems(10, List.of(
            new io.github.shigureyukina.octobus.domain.GroupIDAndLLMName(1, "gpt-4")
        ));
        verify(groupRepository).batchAddItems(11, List.of(
            new io.github.shigureyukina.octobus.domain.GroupIDAndLLMName(1, "gpt-4-turbo")
        ));
    }

    @Test
    void noMatchingGroups() {
        var channel = new Channel(1, "test", "openai/chat_completions", true, null, List.of(), "gpt-4,gpt-4-turbo", null, false, false, 2, null, null, null, null, null);
        var groups = List.of(
            new Group(10, "claude-3", 0, null, 0, 0, List.of())
        );
        when(groupRepository.findAll()).thenReturn(groups);

        channelAutoGroup.autoGroup(channel);

        verify(groupRepository, never()).batchAddItems(anyInt(), anyList());
    }
}
