/*
 * Copyright 2000-2013 JetBrains s.r.o.
 * Copyright 2014-2014 AS3Boyan
 * Copyright 2014-2014 Elias Ku
 * Copyright 2017-2018 Ilya Malanin
 * Copyright 2018 Eric Bishton
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.plugins.haxe.ide.completion;

import com.intellij.codeInsight.completion.*;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Pair;
import com.intellij.plugins.haxe.ide.index.HaxeConstructorIndex;
import com.intellij.plugins.haxe.ide.index.HaxeConstructorInfo;
import com.intellij.plugins.haxe.ide.lookup.HaxeConstructorLookupElement;
import com.intellij.plugins.haxe.util.HaxeResolveUtil;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.util.ProcessingContext;
import com.intellij.util.Processor;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import static com.intellij.plugins.haxe.ide.completion.HaxeCommonCompletionPattern.*;

public class HaxeConstructorCompletionContributor extends CompletionContributor {
  public HaxeConstructorCompletionContributor() {
    extend(CompletionType.BASIC, identifierInNewExpression,
           new CompletionProvider<>() {
             @Override
             protected void addCompletions(@NotNull CompletionParameters parameters,
                                           ProcessingContext context,
                                           @NotNull CompletionResultSet result) {
               long start = HaxeCompletionPerformanceTracker.now();
               try {
                 final PsiFile file = parameters.getOriginalFile();
                 addVariantsFromIndex(result, file);
               } finally {
                 HaxeCompletionPerformanceTracker.recordContributor("HaxeConstructorCompletionContributor", parameters, start);
               }
             }
           });
  }

  private static void addVariantsFromIndex(final CompletionResultSet resultSet, final PsiFile targetFile) {

    final Project project = targetFile.getProject();
    final GlobalSearchScope scope = HaxeResolveUtil.getScopeForElement(targetFile);
    final MyProcessor processor = new MyProcessor(resultSet, targetFile);
    HaxeConstructorIndex.processAll(project, processor, scope);
  }


  private static class MyProcessor implements Processor<Pair<String, HaxeConstructorInfo>> {
    private final CompletionResultSet myResultSet;
    @Nullable private final PsiElement element;

    private MyProcessor(CompletionResultSet resultSet, @Nullable PsiElement element) {
      myResultSet = resultSet;
      this.element = element;
    }

    @Override
    public boolean process(Pair<String, HaxeConstructorInfo> pair) {
      HaxeConstructorInfo info = pair.getSecond();
        myResultSet.addElement(new HaxeConstructorLookupElement(
          info.getClassName(),
          info.getPackageName(),
          info.hasParameters(),
          info.getType(),
          element));
      return true;
    }
  }
}
