package com.nebula.spock.actions;

import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.LangDataKeys;
import com.intellij.openapi.actionSystem.PlatformDataKeys;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.search.searches.ReferencesSearch;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.usageView.UsageInfo;
import com.intellij.usages.*;
import com.intellij.util.Query;
import org.jetbrains.annotations.NotNull;

import java.util.*;

/**
 * Created by tsaravana on 6/13/2015.
 */
public class FindBusAction extends AnAction {

    private String methodName;
    private String busObjectName;
    private Collection<PsiReference> allReference;
    private final List<Usage> usages = new ArrayList<Usage>();
    private Map<String, String> allowedMethods;

    public void actionPerformed(AnActionEvent e) {
        System.out.println("Action Executing");

        final PsiFile psiFile = e.getData(LangDataKeys.PSI_FILE);
        final Editor editor = e.getData(PlatformDataKeys.EDITOR);
        if (psiFile == null || editor == null) {
            e.getPresentation().setVisible(false);
        }
        final int offset = editor.getCaretModel().getOffset();
        final PsiElement psiElement = psiFile.findElementAt(offset);

        final PsiMethodCallExpression psiMethodCallExpression = PsiTreeUtil.getParentOfType(psiElement, PsiMethodCallExpression.class);
        final PsiMethod psiMethod = psiMethodCallExpression.resolveMethod();

        methodName = psiMethod.getName();
        busObjectName = getBusObjectName(psiMethodCallExpression);

        System.out.println(methodName);
        System.out.println(busObjectName);

        if ("send".equals(methodName) || "sendBusEvent".equals(methodName)) {
            // Find the Register usages
            allReference = searchAllRegisterEvents(e.getProject());
        } else if ("register".equals(methodName) || "registerBusReceiver".equals(methodName)) {
            // Find the Send usages
            allReference = searchAllSendEvents(e.getProject());
        }

        System.out.println(allReference.size());

        for (PsiReference reference : allReference) {
            if (reference instanceof PsiReferenceExpression) {
                final PsiMethodCallExpression psiMethodCallExpression1 = (PsiMethodCallExpression) ((PsiReferenceExpression) reference).getParent();
                System.out.println(getBusObjectName(psiMethodCallExpression1));
                if (busObjectName.equalsIgnoreCase(getBusObjectName(psiMethodCallExpression1))) {
                    final UsageInfo usageInfo = new UsageInfo(reference);
                    Usage usage = new UsageInfo2UsageAdapter(usageInfo);
                    usages.add(usage);
                }
            }
        }

        // Publish the results to the Find dock
        UsageViewManager.getInstance(e.getProject()).showUsages(UsageTarget.EMPTY_ARRAY, usages.toArray(new Usage[usages.size()]), new UsageViewPresentation());
        destroy();
    }

    private void destroy() {
        allReference.clear();
        usages.clear();
    }

    private Collection<PsiReference> searchAllRegisterEvents(Project project) {
        final Collection<PsiReference> register = searchAllMethods(project, "com.epocrates.stm.bus.STBus", "register");
        final Collection<PsiReference> registerBusReceiver = searchAllMethods(project, "com.epocrates.stm.ui.fragment.STBaseFragment", "registerBusReceiver");
        register.addAll(registerBusReceiver);
        return register;
    }

    private Collection<PsiReference> searchAllSendEvents(Project project) {
        final Collection<PsiReference> send = searchAllMethods(project, "com.epocrates.stm.bus.STBus", "send");
        final Collection<PsiReference> sendBusEvent = searchAllMethods(project, "com.epocrates.stm.ui.fragment.STBaseFragment", "sendBusEvent");
        send.addAll(sendBusEvent);
        return send;
    }

    private Collection<PsiReference> searchAllMethods(Project project, String className, String methodName) {
        final JavaPsiFacade psiFacade = JavaPsiFacade.getInstance(project);
        final PsiClass psiClass = psiFacade.findClass(className, GlobalSearchScope.allScope(project));
        final PsiMethod[] psiMethods = psiClass.findMethodsByName(methodName, false);
        final Query<PsiReference> search = ReferencesSearch.search(psiMethods[0]);
        return search.findAll();
    }


    private String getBusObjectName(PsiMethodCallExpression psiMethodCallExpression) {
        final PsiExpressionList argumentList = psiMethodCallExpression.getArgumentList();
        final PsiExpression[] expressions = argumentList.getExpressions();
        final PsiJavaCodeReferenceElement classOrAnonymousClassReference;
        if (expressions[0] instanceof PsiNewExpression) {
            classOrAnonymousClassReference = ((PsiNewExpression) expressions[0]).getClassOrAnonymousClassReference();
            return classOrAnonymousClassReference.getQualifiedName();
        }
        return null;
    }

    @Override
    public void update(@NotNull AnActionEvent e) {
        initialize();
        final PsiFile psiFile = e.getData(LangDataKeys.PSI_FILE);
        final Editor editor = e.getData(PlatformDataKeys.EDITOR);
        if (psiFile == null || editor == null) {
            e.getPresentation().setVisible(false);
            return;
        }

        final int offset = editor.getCaretModel().getOffset();
        final PsiElement psiElement = psiFile.findElementAt(offset);

        final PsiMethodCallExpression psiMethodCallExpression = PsiTreeUtil.getParentOfType(psiElement, PsiMethodCallExpression.class);
        if (psiMethodCallExpression == null) {
            e.getPresentation().setVisible(false);
            return;
        }

        final PsiMethod psiMethod = psiMethodCallExpression.resolveMethod();
        if (psiMethod == null || !allowedMethods.keySet().contains(psiMethod.getName()) || !allowedMethods.values().contains(((PsiClass) psiMethod.getParent()).getQualifiedName())) {
            e.getPresentation().setVisible(false);
            return;
        }
        System.out.println(psiMethod.getName());
        e.getPresentation().setVisible(true);
    }

    private void initialize() {
        if (allowedMethods == null) {
            allowedMethods = new HashMap<String, String>();
            allowedMethods.put("send", "com.epocrates.stm.bus.STBus");
            allowedMethods.put("sendBusEvent", "com.epocrates.stm.ui.fragment.STBaseFragment");
            allowedMethods.put("register", "com.epocrates.stm.bus.STBus");
            allowedMethods.put("registerBusReceiver", "com.epocrates.stm.ui.fragment.STBaseFragment");
        }
    }
}
