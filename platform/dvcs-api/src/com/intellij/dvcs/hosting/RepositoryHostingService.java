// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.dvcs.hosting;

import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.NotNull;

public interface RepositoryHostingService {
  @NotNull
  String getServiceDisplayName();

  @NotNull
  RepositoryListLoader getRepositoryListLoader(@NotNull Project project);
}
