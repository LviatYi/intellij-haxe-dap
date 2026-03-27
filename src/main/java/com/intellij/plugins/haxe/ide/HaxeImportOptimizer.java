/*
 * Copyright 2000-2013 JetBrains s.r.o.
 * Copyright 2014-2014 AS3Boyan
 * Copyright 2014-2014 Elias Ku
 * Copyright 2017 Eric Bishton
 * Copyright 2017-2017 Ilya Malanin
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
package com.intellij.plugins.haxe.ide;

import com.intellij.injected.editor.VirtualFileWindow;
import com.intellij.lang.ImportOptimizer;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.roots.ProjectRootManager;
import com.intellij.openapi.util.EmptyRunnable;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.plugins.haxe.lang.lexer.HaxeTokenTypeSets;
import com.intellij.plugins.haxe.lang.lexer.HaxeTokenTypes;
import com.intellij.plugins.haxe.lang.psi.HaxeFile;
import com.intellij.plugins.haxe.lang.psi.HaxeImportStatement;
import com.intellij.plugins.haxe.lang.psi.HaxeUsingStatement;
import com.intellij.plugins.haxe.model.HaxeFileModel;
import com.intellij.plugins.haxe.util.HaxeDebugTimeLog;
import com.intellij.plugins.haxe.util.HaxeImportUtil;
import com.intellij.psi.*;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.PsiUtilCore;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;

/**
 * Created by fedorkorotkov.
 */
public class HaxeImportOptimizer implements ImportOptimizer {
  @Override
  public boolean supports(PsiFile file) {
    return file instanceof HaxeFile;
  }

  @NotNull
  @Override
  public Runnable processFile(final PsiFile file) {
    VirtualFile vFile = file.getVirtualFile();
    if (vFile instanceof VirtualFileWindow) vFile = ((VirtualFileWindow)vFile).getDelegate();
    if (vFile == null || !ProjectRootManager.getInstance(file.getProject()).getFileIndex().isInSourceContent(vFile)) {
      return EmptyRunnable.INSTANCE;
    }

    return () -> optimizeImports(file);
  }

  private static void optimizeImports(final PsiFile file) {
    HaxeDebugTimeLog timeLog = HaxeDebugTimeLog.startNew("optimizeImports for file " + file.getName(),
                                                         HaxeDebugTimeLog.Since.StartAndPrevious);
    removeUnusedImports(file);
    PsiDocumentManager.getInstance(file.getProject()).commitAllDocuments();
    reorderImports2(file);
    ensureBlankLineAfterLastImport(file);

    timeLog.stamp("Finished reordering imports.");
    timeLog.print();
  }

  private static void removeUnusedImports(PsiFile file) {
    PsiUtilCore.ensureValid(file);

    for (HaxeImportStatement unusedImportStatement : HaxeImportUtil.findUnusedImports(file)) {
      unusedImportStatement.delete();
    }

    // TODO Remove unused usings.
  }

  private static void reorderImports(final PsiFile file) {
    HaxeFileModel fileModel = HaxeFileModel.fromElement(file);
    List<HaxeImportStatement> allImports = fileModel == null ? new ArrayList<>() : fileModel.getImportStatements();

    if (allImports.size() < 2) {
      return;
    }

    final HaxeImportStatement firstImport = allImports.get(0);
    int startOffset = firstImport.getStartOffsetInParent();
    final HaxeImportStatement lastImport = allImports.get(allImports.size() - 1);
    int endOffset = lastImport.getStartOffsetInParent() + lastImport.getTextLength();

    // We assume the common practice of placing all imports in a single "block" at the top of a file. If there is something else (comments,
    // code, etc) there we just stop reordering to prevent data loss.
    for (PsiElement child : file.getChildren()) {
      int childOffset = child.getStartOffsetInParent();
      if (childOffset >= startOffset && childOffset <= endOffset
          && !(child instanceof HaxeImportStatement)
          && !(child instanceof PsiWhiteSpace)) {
        return;
      }
    }

    List<String> sortedImports = new ArrayList<>();

    for (HaxeImportStatement currentImport : allImports) {
      sortedImports.add(currentImport.getText());
    }

    sortedImports.sort(String::compareToIgnoreCase);

    final PsiDocumentManager psiDocumentManager = PsiDocumentManager.getInstance(file.getProject());
    final Document document = psiDocumentManager.getDocument(file);
    if (document != null) {
      final PsiDocumentManager documentManager = PsiDocumentManager.getInstance(file.getProject());

      /* This operation trims the document if necessary (e.g. it happens with "\n" at the very beginning).
         Need to reevaluate offsets here.
       */
      documentManager.doPostponedOperationsAndUnblockDocument(document);

      // Reevaluating offset values according to the previous comment.
      startOffset = firstImport.getStartOffsetInParent();
      endOffset = lastImport.getStartOffsetInParent() + lastImport.getTextLength();

      document.deleteString(startOffset, endOffset);

      StringBuilder sortedImportsText = new StringBuilder();
      for (String sortedImport : sortedImports) {
        sortedImportsText.append(sortedImport);
        sortedImportsText.append("\n");
      }
      // Removes last "\n".
      CharSequence sortedImportsTextTrimmed = sortedImportsText.subSequence(0, sortedImportsText.length() - 1);

      documentManager.doPostponedOperationsAndUnblockDocument(document);
      document.insertString(startOffset, sortedImportsTextTrimmed);
    }

    // TODO Reorder usings.
  }

  private static void reorderImports2(final PsiFile file) {
    HaxeFile haxeFile = (HaxeFile)file;
    //var macroIndex = MacroIndex.buildMacroIndex(haxeFile);
    List<List<HaxeImportStatement>> importSections = haxeFile.getImportStatementSections()
      .stream()
      //.map(section -> section
      //  .stream()
      //  .filter(item -> {
      //    int offset = item.getTextRange().getStartOffset();
      //    return !macroIndex.isInMacro(offset);
      //  })
      //  .toList()
      //)
      .filter(section -> section.size() > 1)
      .toList();

    if (importSections.isEmpty()) {
      return;
    }

    final PsiDocumentManager psiDocumentManager = PsiDocumentManager.getInstance(file.getProject());
    final Document document = psiDocumentManager.getDocument(file);
    if (document != null) {
      final PsiDocumentManager documentManager = PsiDocumentManager.getInstance(file.getProject());

      /* This operation trims the document if necessary (e.g. it happens with "\n" at the very beginning).
         Need to reevaluate offsets here.
      */
      documentManager.doPostponedOperationsAndUnblockDocument(document);

      for (int sectionIndex = importSections.size() - 1; sectionIndex >= 0; sectionIndex--) {
        List<HaxeImportStatement> importSection = importSections.get(sectionIndex);
        List<String> sortedImports = new ArrayList<>(importSection.stream().map(PsiElement::getText).toList());
        sortedImports.sort(String::compareToIgnoreCase);

        int sectionStart = importSection.get(0).getTextRange().getStartOffset();
        int sectionEnd = importSection.get(importSection.size() - 1).getTextRange().getEndOffset();

        document.deleteString(sectionStart, sectionEnd);
        document.insertString(sectionStart, String.join("\n", sortedImports));
        documentManager.doPostponedOperationsAndUnblockDocument(document);
        documentManager.commitDocument(document);
      }
    }
  }

  private static void ensureBlankLineAfterLastImport(final PsiFile file) {
    HaxeFile haxeFile = (HaxeFile)file;
    List<HaxeImportStatement> importStatements = haxeFile.getImportStatements();
    if (importStatements.isEmpty()) {
      return;
    }

    HaxeImportStatement lastImport = importStatements.get(importStatements.size() - 1);
    PsiElement nextSibling = lastImport.getNextSibling();
    while (nextSibling instanceof PsiWhiteSpace) {
      nextSibling = nextSibling.getNextSibling();
    }

    if (nextSibling == null
        || nextSibling instanceof HaxeImportStatement
        || nextSibling instanceof HaxeUsingStatement
        || nextSibling instanceof PsiComment) {
      return;
    }

    Document document = PsiDocumentManager.getInstance(file.getProject()).getDocument(file);
    if (document == null) {
      return;
    }

    PsiDocumentManager documentManager = PsiDocumentManager.getInstance(file.getProject());
    documentManager.doPostponedOperationsAndUnblockDocument(document);

    int whitespaceStart = lastImport.getTextRange().getEndOffset();
    int whitespaceEnd = nextSibling.getTextRange().getStartOffset();
    if (whitespaceEnd < whitespaceStart) {
      return;
    }

    document.replaceString(whitespaceStart, whitespaceEnd, "\n\n");
    documentManager.doPostponedOperationsAndUnblockDocument(document);
    documentManager.commitDocument(document);
  }
}

class MacroIndex {
  public final List<TextRange> macroRanges = new ArrayList<>();

  public final List<MacroHeader> headers = new ArrayList<>();

  public static MacroIndex buildMacroIndex(@NotNull HaxeFile file) {
    MacroIndex macroIndex = new MacroIndex();

    Deque<Integer> ifStack = new ArrayDeque<>();
    int depth = 0;

    var leaf = PsiTreeUtil.getDeepestFirst(file);
    while (leaf != null) {
      if (leaf instanceof PsiComment) {
        IElementType tt = ((PsiComment)leaf).getTokenType();
        if (tt == HaxeTokenTypes.PPIF) {
          int start = leaf.getTextRange().getStartOffset();
          ifStack.push(start);
          depth++;

          String cond = grabConditionAfterIfOrElseIf(leaf);
          macroIndex.headers.add(new MacroHeader(leaf.getTextRange(), cond, depth));
        }
        else if (tt == HaxeTokenTypes.PPELSEIF) {
          String cond = grabConditionAfterIfOrElseIf(leaf);
          macroIndex.headers.add(new MacroHeader(leaf.getTextRange(), cond, depth));
        }
        else if (tt == HaxeTokenTypes.PPELSE) {
          macroIndex.headers.add(new MacroHeader(leaf.getTextRange(), null, depth));
        }
        else if (tt == HaxeTokenTypes.PPEND) {
          if (!ifStack.isEmpty()) {
            int start = ifStack.pop();
            depth--;
            int end = leaf.getTextRange().getEndOffset();
            macroIndex.macroRanges.add(new TextRange(start, end));
          }
        }
      }

      leaf = PsiTreeUtil.nextLeaf(leaf, true);
    }

    return macroIndex;
  }

  static String grabConditionAfterIfOrElseIf(@NotNull PsiElement ifOrElseIf) {
    PsiElement e = ifOrElseIf;
    var result = "";

    while (true) {
      do {
        e = PsiTreeUtil.nextLeaf(e, true);
      }
      while (e instanceof PsiWhiteSpace);

      if (e instanceof PsiComment && ((PsiComment)e).getTokenType() == HaxeTokenTypeSets.PPEXPRESSION) {
        result += e.getText();
      }
      else {
        break;
      }
    }

    result = result.trim();
    return result.isEmpty() ? null : result;
  }

  public boolean isInMacro(int offset) {
    int lo = 0, hi = this.macroRanges.size() - 1;
    while (lo <= hi) {
      int mid = (lo + hi) >>> 1;
      TextRange r = this.macroRanges.get(mid);
      if (offset < r.getStartOffset()) {
        hi = mid - 1;
      }
      else if (offset >= r.getEndOffset()) {
        lo = mid + 1;
      }
      else {
        return true;
      }
    }

    return false;
  }
}

record MacroHeader(TextRange range, @Nullable String condition, int depth) {
}
