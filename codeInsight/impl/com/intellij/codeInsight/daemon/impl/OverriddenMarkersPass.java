
package com.intellij.codeInsight.daemon.impl;

import com.intellij.codeInsight.CodeInsightUtil;
import com.intellij.codeInsight.daemon.DaemonCodeAnalyzer;
import com.intellij.codeInsight.daemon.impl.analysis.HighlightUtil;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.progress.ProcessCanceledException;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.IconLoader;
import com.intellij.psi.*;
import com.intellij.psi.search.searches.ClassInheritorsSearch;
import com.intellij.psi.util.MethodSignature;
import com.intellij.psi.util.MethodSignatureUtil;
import com.intellij.psi.util.PsiUtil;
import com.intellij.psi.util.TypeConversionUtil;
import com.intellij.util.Processor;
import com.intellij.util.containers.HashMap;
import com.intellij.util.containers.HashSet;
import com.intellij.javaee.ejb.role.EjbRolesUtil;
import com.intellij.javaee.ejb.role.EjbMethodRole;
import com.intellij.javaee.ejb.role.EjbDeclMethodRole;

import javax.swing.*;
import java.util.*;

public class OverriddenMarkersPass extends TextEditorHighlightingPass {
  private static final Icon OVERRIDEN_METHOD_MARKER_RENDERER = IconLoader.getIcon("/gutter/overridenMethod.png");
  private static final Icon IMPLEMENTED_METHOD_MARKER_RENDERER = IconLoader.getIcon("/gutter/implementedMethod.png");
  private static final Icon IMPLEMENTED_INTERFACE_MARKER_RENDERER = IconLoader.getIcon("/gutter/implementedMethod.png");
  private static final Icon SUBCLASSED_CLASS_MARKER_RENDERER = IconLoader.getIcon("/gutter/overridenMethod.png");

  private final Project myProject;
  private final PsiFile myFile;
  private final Document myDocument;
  private final int myStartOffset;
  private final int myEndOffset;

  private Collection<LineMarkerInfo> myMarkers = Collections.emptyList();

  public OverriddenMarkersPass(
    Project project,
    PsiFile file,
    Document document,
    int startOffset,
    int endOffset
    ) {
    super(document);
    myProject = project;
    myFile = file;
    myDocument = document;
    myStartOffset = startOffset;
    myEndOffset = endOffset;
  }

  public void doCollectInformation(ProgressIndicator progress) {
    PsiElement[] psiRoots = myFile.getPsiRoots();
    for (final PsiElement psiRoot : psiRoots) {
      if (!HighlightUtil.isRootHighlighted(psiRoot)) continue;
      List<PsiElement> elements = CodeInsightUtil.getElementsInRange(psiRoot, myStartOffset, myEndOffset);
      myMarkers = collectLineMarkers(elements);
    }
  }

  public void doApplyInformationToEditor() {
    UpdateHighlightersUtil.setLineMarkersToEditor(
      myProject, myDocument, myStartOffset, myEndOffset,
      myMarkers, UpdateHighlightersUtil.OVERRIDEN_MARKERS_GROUP);

    DaemonCodeAnalyzerImpl daemonCodeAnalyzer = (DaemonCodeAnalyzerImpl)DaemonCodeAnalyzer.getInstance(myProject);
    daemonCodeAnalyzer.getFileStatusMap().markFileUpToDate(myDocument, FileStatusMap.OVERRIDEN_MARKERS);
  }

  public int getPassId() {
    return Pass.UPDATE_OVERRIDEN_MARKERS;
  }

  private static Collection<LineMarkerInfo> collectLineMarkers(List<PsiElement> elements) throws ProcessCanceledException {
    ApplicationManager.getApplication().assertReadAccessAllowed();

    List<LineMarkerInfo> array = new ArrayList<LineMarkerInfo>();
    Set<PsiMethod> methods = new HashSet<PsiMethod>();
    for (PsiElement element : elements) {
      ProgressManager.getInstance().checkCanceled();
      if (element instanceof PsiIdentifier) {
        if (element.getParent() instanceof PsiMethod) {
          final PsiMethod method = ((PsiMethod)element.getParent());
          if (element.equals(method.getNameIdentifier()) && PsiUtil.canBeOverriden(method)) {
            methods.add(method);
          }
        }
        else if (element.getParent() instanceof PsiClass && !(element.getParent() instanceof PsiTypeParameter)) {
          collectInheritingClasses(element, array);
        }
      }
    }
    if (!methods.isEmpty()) {
      collectOverridingMethods(methods, array);
    }
    return array;
  }

  private static void collectInheritingClasses(PsiElement element, List<LineMarkerInfo> result) {
    PsiClass aClass = (PsiClass) element.getParent();
    if (element.equals(aClass.getNameIdentifier())) {
      if (!aClass.hasModifierProperty(PsiModifier.FINAL)) {
        if ("java.lang.Object".equals(aClass.getQualifiedName())) return; // It's useless to have overriden markers for object.

        final PsiClass inheritor = ClassInheritorsSearch.search(aClass, false).findFirst();
        if (inheritor != null) {
          int offset = element.getTextRange().getStartOffset();
          LineMarkerInfo info = new LineMarkerInfo(LineMarkerInfo.SUBCLASSED_CLASS, aClass, offset, aClass.isInterface() ? IMPLEMENTED_INTERFACE_MARKER_RENDERER : SUBCLASSED_CLASS_MARKER_RENDERER);

          result.add(info);
        }
      }
    }
  }

  private static void collectOverridingMethods(Set<PsiMethod> methods, List<LineMarkerInfo> result) {
    final Set<PsiMethod> overridden = new HashSet<PsiMethod>();
    Map<PsiClass, List<PsiMethod>> classesToMethods = new HashMap<PsiClass, List<PsiMethod>>();
    for (PsiMethod method : methods) {
      final PsiClass parentClass = method.getContainingClass();
      if (!"java.lang.Object".equals(parentClass.getQualifiedName())) {
        List<PsiMethod> hisMethods = classesToMethods.get(parentClass);
        if (hisMethods == null) {
          hisMethods = new ArrayList<PsiMethod>();
          classesToMethods.put(parentClass, hisMethods);
        }
        hisMethods.add(method);
      }
    }

    for (final PsiClass aClass : classesToMethods.keySet()) {
      final List<PsiMethod> hisMethods = classesToMethods.get(aClass);
      ClassInheritorsSearch.search(aClass).forEach(new Processor<PsiClass>() {
        public boolean process(final PsiClass inheritor) {
          PsiSubstitutor substitutor = TypeConversionUtil.getSuperClassSubstitutor(aClass, inheritor, PsiSubstitutor.EMPTY);
          for (Iterator<PsiMethod> iterator = hisMethods.iterator(); iterator.hasNext();) {
            PsiMethod hisMethod = iterator.next();
            final MethodSignature hisSignature = hisMethod.getSignature(substitutor);
            final PsiMethod derived = MethodSignatureUtil.findMethodBySignature(inheritor, hisSignature, false);
            if (derived != null && inheritor.getManager().getResolveHelper().isAccessible(hisMethod, derived, null)) {
              iterator.remove();
              overridden.add(hisMethod);
            }
          }

          return !hisMethods.isEmpty();
        }
      });
      if (hisMethods.size() != 0 && !(EjbRolesUtil.getEjbRolesUtil().getEjbRoles(aClass).length > 0)) {
        for (Iterator iterator = hisMethods.iterator(); iterator.hasNext();) {
          PsiMethod hisMethod = (PsiMethod)iterator.next();
          for (EjbMethodRole role : EjbRolesUtil.getEjbRolesUtil().getEjbRoles(hisMethod)) {
            if ((role instanceof EjbDeclMethodRole) && ((EjbDeclMethodRole)role).findAllImplementations().length > 0) {
              iterator.remove();
              overridden.add(hisMethod);
              break;
            }
          }
        }
      }
    }

    for (PsiMethod method : overridden) {
      boolean overrides;
      overrides = !method.hasModifierProperty(PsiModifier.ABSTRACT);

      int offset = method.getNameIdentifier().getTextRange().getStartOffset();
      LineMarkerInfo info = new LineMarkerInfo(LineMarkerInfo.OVERRIDEN_METHOD, method, offset,
                                               overrides ? OVERRIDEN_METHOD_MARKER_RENDERER : IMPLEMENTED_METHOD_MARKER_RENDERER);

      result.add(info);
    }
  }
}