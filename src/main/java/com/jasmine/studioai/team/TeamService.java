package com.jasmine.studioai.team;

import com.jasmine.studioai.model.User;
import com.jasmine.studioai.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class TeamService {

    private final UserRepository userRepository;

    public List<User> getTeamMembers(String teamId) {
        return List.of();
    }

    public void addMemberToTeam(String teamId, String userId) {
    }

    public void removeMemberFromTeam(String teamId, String userId) {
    }

    public String createTeam(String name, String ownerId) {
        return "team-" + System.currentTimeMillis();
    }
}
