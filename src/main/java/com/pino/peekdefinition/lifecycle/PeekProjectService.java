package com.pino.peekdefinition.lifecycle;

import com.intellij.openapi.Disposable;
import com.intellij.openapi.components.Service;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.NotNull;

/** Parent disposable for every Peek of a project, so project close and plugin unload clean up all of them. */
@Service(Service.Level.PROJECT)
public final class PeekProjectService implements Disposable {

    public static @NotNull PeekProjectService getInstance(@NotNull Project project) {
        return project.getService(PeekProjectService.class);
    }

    @Override
    public void dispose() {
    }
}
