package com.reqflow.dto;

import java.util.List;

public record WorkspaceMembersResult(boolean canManage, List<WorkspaceMemberView> members) {}
