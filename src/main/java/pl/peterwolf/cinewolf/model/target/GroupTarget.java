package pl.peterwolf.cinewolf.model.target;

import pl.peterwolf.cinewolf.model.GroupFocusMode;
import pl.peterwolf.cinewolf.model.TargetKind;
import pl.peterwolf.cinewolf.model.TargetReference;

import java.util.List;
import java.util.Objects;
import java.util.UUID;

public record GroupTarget(
        UUID groupId,
        List<TargetReference> members,
        GroupFocusMode focusMode,
        TargetReference primaryMember
) implements CinematicTarget {
    public GroupTarget {
        Objects.requireNonNull(groupId, "groupId");
        members = List.copyOf(Objects.requireNonNullElse(members, List.of()));
        if (members.isEmpty()) throw new IllegalArgumentException("Group target requires at least one member");
        Objects.requireNonNull(focusMode, "focusMode");
        primaryMember = primaryMember == null ? members.getFirst() : primaryMember;
    }

    @Override
    public UUID id() {
        return groupId;
    }

    @Override
    public TargetKind kind() {
        return TargetKind.GROUP;
    }

    @Override
    public String displayName() {
        return "Group(" + members.size() + ")";
    }
}
