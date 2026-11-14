package com.zhengte.platform.converter;

import com.zhengte.platform.entity.BatchRecord;
import com.zhengte.platform.entity.CrRecord;
import com.zhengte.platform.entity.DeploymentRecord;
import com.zhengte.platform.entity.EnvStateRecord;
import com.zhengte.platform.entity.ReleaseHistoryRecord;
import com.zhengte.platform.entity.RequirementBranchRecord;
import com.zhengte.platform.entity.RequirementRecord;
import com.zhengte.platform.entity.StepRecord;
import com.zhengte.platform.entity.Tables;
import com.zhengte.platform.entity.UserAccount;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.ReportingPolicy;
import org.mapstruct.factory.Mappers;

@Mapper(unmappedTargetPolicy = ReportingPolicy.ERROR)
public interface RecordMapper {

    RecordMapper INSTANCE = Mappers.getMapper(RecordMapper.class);

    UserAccount toUser(Tables.User row);

    RequirementRecord toRequirement(Tables.Requirement row);

    RequirementBranchRecord toRequirementBranch(Tables.RequirementBranch row);

    CrRecord toCr(Tables.CrRequest row);

    CrRecord toCrPending(Tables.CrPending row);

    EnvStateRecord toEnvState(Tables.EnvState row);

    BatchRecord toBatch(Tables.DeployBatch row);

    DeploymentRecord toDeployment(Tables.Deployment row);

    StepRecord toStep(Tables.DeploymentStep row);

    ReleaseHistoryRecord toReleaseHistory(Tables.ReleaseHistory row);
}
