package ru.muslim.tajwid.web.dto;

public record PublicChildrenStatsResponse(
    long totalUsers,
    long usersWithChildren,
    long usersWithoutChildren,
    long usersWithChildrenStudyQuranTrue,
    long usersWithChildrenStudyQuranFalse,
    long usersWithChildrenStudyQuranUnknown
) {
}
