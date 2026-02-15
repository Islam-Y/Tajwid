package ru.muslim.tajwid.web.dto;

import java.util.List;

public record AdminExportSnapshotResponse(
    List<AdminExportUserRow> users,
    List<AdminExportFlowContextRow> flowContexts,
    List<AdminExportUserTagRow> userTags,
    List<AdminExportReferralLinkUsageRow> referralLinkUsages
) {
}
