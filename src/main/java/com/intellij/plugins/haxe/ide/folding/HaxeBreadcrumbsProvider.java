package com.intellij.plugins.haxe.ide.folding;

import com.intellij.lang.Language;
import com.intellij.openapi.util.NlsSafe;
import com.intellij.plugins.haxe.HaxeComponentType;
import com.intellij.plugins.haxe.HaxeLanguage;
import com.intellij.plugins.haxe.lang.psi.*;
import com.intellij.psi.PsiElement;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.ui.breadcrumbs.BreadcrumbsProvider;
import icons.HaxeIcons;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;

public class HaxeBreadcrumbsProvider implements BreadcrumbsProvider {
  @Override
  public Language[] getLanguages() {
    return new HaxeLanguage[]{HaxeLanguage.INSTANCE};
  }

  @Override
  public boolean acceptElement(@NotNull PsiElement element) {
    return switch (element) {
      case HaxeClassDeclaration ignore -> true;
      case HaxeExternClassDeclaration ignore -> true;
      case HaxeAbstractTypeDeclaration ignore -> true;
      case HaxeEnumDeclaration ignore -> true;
      case HaxeInterfaceDeclaration ignore -> true;
      case HaxeExternInterfaceDeclaration ignore -> true;
      case HaxeMethodDeclaration ignore -> true;
      case HaxeLocalFunctionDeclaration ignore -> true;
      case HaxeTypedefDeclaration ignore -> true;
      case HaxeFile ignore -> true;
      default -> false;
    };
  }

  @Override
  public boolean acceptStickyElement(@NotNull PsiElement element) {
    if (element instanceof HaxeFile) {
      return false;
    }

    return BreadcrumbsProvider.super.acceptStickyElement(element);
  }

  @Override
  public @NotNull @NlsSafe String getElementInfo(@NotNull PsiElement element) {
    String name = switch (element) {
      case HaxeClassDeclaration declaration -> declaration.getName();
      case HaxeExternClassDeclaration declaration -> declaration.getName();
      case HaxeAbstractTypeDeclaration declaration -> declaration.getName();
      case HaxeEnumDeclaration declaration -> declaration.getName();
      case HaxeInterfaceDeclaration declaration -> declaration.getName();
      case HaxeExternInterfaceDeclaration declaration -> declaration.getName();
      case HaxeMethodDeclaration declaration -> declaration.getName() + "()";
      case HaxeLocalFunctionDeclaration declaration -> declaration.getName() + "()";
      case HaxeTypedefDeclaration declaration -> declaration.getName();
      case HaxeFile declaration -> declaration.getName();
      default -> null;
    };

    return name == null ? "<unknown>" : name;
  }

  @Override
  public @Nullable PsiElement getParent(@NotNull PsiElement element) {
    var classParent = switch (element) {
      case HaxeMethodDeclaration declaration -> declaration.getContainingClass();
      case HaxeLocalFunctionDeclaration declaration -> declaration.getContainingClass();
      default -> null;
    };
    if (classParent != null) {
      return classParent;
    }

    return switch (element) {
      case HaxeClassDeclaration declaration -> declaration.getContainingFile();
      case HaxeExternClassDeclaration declaration -> declaration.getContainingFile();
      case HaxeAbstractTypeDeclaration declaration -> declaration.getContainingFile();
      case HaxeEnumDeclaration declaration -> declaration.getContainingFile();
      case HaxeInterfaceDeclaration declaration -> declaration.getContainingFile();
      case HaxeExternInterfaceDeclaration declaration -> declaration.getContainingFile();
      case HaxeTypedefDeclaration declaration -> declaration.getContainingFile();
      case HaxeMethodDeclaration declaration -> declaration.getContainingFile();
      case HaxeLocalFunctionDeclaration declaration -> declaration.getContainingFile();
      default -> PsiTreeUtil.getParentOfType(element,
                                             HaxeClassDeclaration.class,
                                             HaxeEnumDeclaration.class,
                                             HaxeInterfaceDeclaration.class,
                                             HaxeTypedefDeclaration.class,
                                             HaxeMethodDeclaration.class);
    };
  }

  @Override
  public @Nullable Icon getElementIcon(@NotNull PsiElement element) {
    if (element instanceof HaxeFile) return HaxeIcons.HAXE_LOGO;
    HaxeComponentType componentType = HaxeComponentType.typeOf(element);
    return componentType == null ? null : componentType.getIcon();
  }
}
