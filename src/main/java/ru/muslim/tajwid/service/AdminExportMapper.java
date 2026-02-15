package ru.muslim.tajwid.service;

import org.mapstruct.Mapper;
import ru.muslim.tajwid.domain.FlowContextEntity;
import ru.muslim.tajwid.domain.ReferralLinkUsageEntity;
import ru.muslim.tajwid.domain.UserEntity;
import ru.muslim.tajwid.domain.UserTagEntity;
import ru.muslim.tajwid.web.dto.AdminExportFlowContextRow;
import ru.muslim.tajwid.web.dto.AdminExportReferralLinkUsageRow;
import ru.muslim.tajwid.web.dto.AdminExportUserRow;
import ru.muslim.tajwid.web.dto.AdminExportUserTagRow;

@Mapper(componentModel = "spring")
public interface AdminExportMapper {

    AdminExportUserRow toUserRow(UserEntity entity);

    AdminExportFlowContextRow toFlowContextRow(FlowContextEntity entity);

    AdminExportUserTagRow toUserTagRow(UserTagEntity entity);

    AdminExportReferralLinkUsageRow toReferralLinkUsageRow(ReferralLinkUsageEntity entity);
}
