package com.intellij.plugins.haxe.ide.folding;

import com.intellij.lang.Language;
import com.intellij.openapi.util.NlsSafe;
import com.intellij.plugins.haxe.HaxeLanguage;
import com.intellij.plugins.haxe.lang.psi.*;
import com.intellij.psi.PsiElement;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.ui.breadcrumbs.BreadcrumbsProvider;
import icons.HaxeIcons;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.util.Objects;

public class HaxeBreadcrumbsProvider implements BreadcrumbsProvider {
  @Override
  public Language[] getLanguages() {
    return new HaxeLanguage[]{HaxeLanguage.INSTANCE};
  }

  @Override
  public boolean acceptElement(@NotNull PsiElement element) {
    return element instanceof HaxeClassDeclaration ||
           element instanceof HaxeEnumDeclaration ||
           element instanceof HaxeInterfaceDeclaration ||
           element instanceof HaxeMethodDeclaration ||
           element instanceof HaxeTypedefDeclaration ||
           element instanceof HaxeFile;
  }

  @Override
  public @NotNull @NlsSafe String getElementInfo(@NotNull PsiElement element) {
    String name = null;
    if (element instanceof HaxeClassDeclaration) {
      name = ((HaxeClassDeclaration)element).getName();
    }
    else if (element instanceof HaxeEnumDeclaration) {
      name = ((HaxeEnumDeclaration)element).getName();
    }
    else if (element instanceof HaxeInterfaceDeclaration) {
      name = ((HaxeInterfaceDeclaration)element).getName();
    }
    else if (element instanceof HaxeMethodDeclaration) {
      name = ((HaxeMethodDeclaration)element).getName() + "()";
    }
    else if (element instanceof HaxeTypedefDeclaration) {
      name = ((HaxeTypedefDeclaration)element).getName();
    }

    return name == null ? "" : name;
  }

  @Override
  public @Nullable PsiElement getParent(@NotNull PsiElement element) {
    if (element instanceof HaxeMethodDeclaration) {
      var classParent = ((HaxeMethodDeclaration)element).getContainingClass();
      if (classParent != null) {
        return classParent;
      }
    }

    if (element instanceof HaxeMethodDeclaration ||
        element instanceof HaxeClassDeclaration ||
        element instanceof HaxeEnumDeclaration ||
        element instanceof HaxeTypedefDeclaration ||
        element instanceof HaxeInterfaceDeclaration) {
      return element.getContainingFile();
    }
    else {
      return PsiTreeUtil.getParentOfType(element,
                                         HaxeClassDeclaration.class,
                                         HaxeEnumDeclaration.class,
                                         HaxeInterfaceDeclaration.class,
                                         HaxeTypedefDeclaration.class,
                                         HaxeMethodDeclaration.class);
    }
  }

  @Override
  public @Nullable Icon getElementIcon(@NotNull PsiElement element) {
    if (element instanceof HaxeClassDeclaration) {
      return HaxeIcons.Class;
    }
    else if (element instanceof HaxeEnumDeclaration) {
      return HaxeIcons.Enum;
    }
    else if (element instanceof HaxeInterfaceDeclaration) {
      return HaxeIcons.Interface;
    }
    else if (element instanceof HaxeMethodDeclaration) {
      return HaxeIcons.Method;
    }
    else if (element instanceof HaxeTypedefDeclaration) {
      return HaxeIcons.Typedef;
    }

    return null;
  }
}
