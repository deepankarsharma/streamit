/*
 * LIRToC.java: convert StreaMIT low IR to C
 * $Id: LIRToC.java,v 1.28 2001-10-26 22:06:55 dmaze Exp $
 */

package at.dms.kjc.lir;

import java.io.StringWriter;
import java.util.Iterator;
import java.util.StringTokenizer;
import java.util.List;
import at.dms.util.InconsistencyException;

import at.dms.kjc.sir.*;
import at.dms.kjc.*;
import at.dms.compiler.*;

public class LIRToC
    extends at.dms.util.Utils
    implements Constants, SLIRVisitor
{

    // ----------------------------------------------------------------------
    // CONSTRUCTORS
    // ----------------------------------------------------------------------

    /**
     * construct a pretty printer object for java code
     */
    public LIRToC() {
        this.str = new StringWriter();
        this.p = new TabbedPrintWriter(str);
    }

    /**
     * construct a pretty printer object for java code
     * @param	fileName		the file into the code is generated
     */
    public LIRToC(TabbedPrintWriter p) {
        this.p = p;
        this.str = null;
        this.pos = 0;
    }

    /**
     * Close the stream at the end
     */
    public void close() {
        p.close();
    }

    public void setPos(int pos) {
        this.pos = pos;
    }

    public String getString() {
        if (str != null)
            return str.toString();
        else
            return null;
    }

    // ----------------------------------------------------------------------
    // TYPE DECLARATION
    // ----------------------------------------------------------------------

    /**
     * prints a compilation unit
     */
    public void visitCompilationUnit(JCompilationUnit self,
                                     JPackageName packageName,
                                     JPackageImport[] importedPackages,
                                     JClassImport[] importedClasses,
                                     JTypeDeclaration[] typeDeclarations) {
        if (packageName.getName().length() > 0) {
            packageName.accept(this);
            if (importedPackages.length + importedClasses.length > 0) {
                newLine();
            }
        }

        for (int i = 0; i < importedPackages.length ; i++) {
            if (!importedPackages[i].getName().equals("java/lang")) {
                importedPackages[i].accept(this);
                newLine();
            }
        }

        for (int i = 0; i < importedClasses.length ; i++) {
            importedClasses[i].accept(this);
            newLine();
        }

        for (int i = 0; i < typeDeclarations.length ; i++) {
            newLine();
            typeDeclarations[i].accept(this);
            newLine();
        }
    }

    // ----------------------------------------------------------------------
    // TYPE DECLARATION
    // ----------------------------------------------------------------------

    /**
     * prints a class declaration
     */
    public void visitClassDeclaration(JClassDeclaration self,
                                      int modifiers,
                                      String ident,
                                      String superName,
                                      CClassType[] interfaces,
                                      JPhylum[] body,
                                      JFieldDeclaration[] fields,
                                      JMethodDeclaration[] methods,
                                      JTypeDeclaration[] decls) {
        LIRToC that = new LIRToC();
        that.className = ident;
        that.visitClassBody(decls, fields, methods, body);
    
        print(that.getString());
    }

    /**
     *
     */
    public void visitClassBody(JTypeDeclaration[] decls,
                               JFieldDeclaration[] fields,
                               JMethodDeclaration[] methods,
                               JPhylum[] body) {
        for (int i = 0; i < decls.length ; i++) {
            decls[i].accept(this);
        }
        if (body != null) {
            for (int i = 0; i < body.length ; i++) {
                if (!(body[i] instanceof JFieldDeclaration)) {
                    body[i].accept(this);
                }
            }
        }

        newLine();
        print("typedef struct " + className + " {");
        pos += TAB_SIZE;
        if (body != null) {
            for (int i = 0; i < body.length ; i++) {
                if (body[i] instanceof JFieldDeclaration) {
                    body[i].accept(this);
                }
            }
        }
        for (int i = 0; i < fields.length; i++) {
            fields[i].accept(this);
        }    

        pos -= TAB_SIZE;
        newLine();
        print("} _" + className + ", *" + className + ";");

        // Print function prototypes for each of the methods.
        declOnly = true;
        for (int i = 0; i < methods.length; i++) {
            methods[i].accept(this);
        }

        declOnly = false;
        for (int i = 0; i < methods.length ; i++) {
            methods[i].accept(this);
        }
    }

    /**
     * prints a class declaration
     */
    public void visitInnerClassDeclaration(JClassDeclaration self,
                                           int modifiers,
                                           String ident,
                                           String superName,
                                           CClassType[] interfaces,
                                           JTypeDeclaration[] decls,
                                           JPhylum[] body,
                                           JFieldDeclaration[] fields,
                                           JMethodDeclaration[] methods) {
        print(" {");
        pos += TAB_SIZE;
        for (int i = 0; i < decls.length ; i++) {
            decls[i].accept(this);
        }
        for (int i = 0; i < fields.length; i++) {
            fields[i].accept(this);
        }
        for (int i = 0; i < methods.length ; i++) {
            methods[i].accept(this);
        }
        for (int i = 0; i < body.length ; i++) {
            body[i].accept(this);
        }
        pos -= TAB_SIZE;
        newLine();
        print("}");
    }

    /**
     * prints an interface declaration
     */
    public void visitInterfaceDeclaration(JInterfaceDeclaration self,
                                          int modifiers,
                                          String ident,
                                          CClassType[] interfaces,
                                          JPhylum[] body,
                                          JMethodDeclaration[] methods) {
        /* If an interface declaration gets through this far, it means
         * that there is a portal interface that can have messages sent
         * through it.  We can ignore everything about the interface
         * but the methods.  For each method, we need to generate a
         * parameter structure and a correct send_message() wrapper. */
        for (int i = 0; i < methods.length; i++) {
            CMethod mth = methods[i].getMethod();
            CType[] params = mth.getParameters();
            String name = ident + "_" + methods[i].getName();
            String pname = name + "_params";

            // Print the parameter structure.
            newLine();
            print("typedef struct " + pname + " {");
            pos += TAB_SIZE;
            for (int j = 0; j < params.length; j++) {
                newLine();
                print(params[j] + " p" + j + ";");
            }
            pos -= TAB_SIZE;
            newLine();
            print("} _" + pname + ", *" + pname + ";");

            // And now print a wrapper for send_message().
            newLine();
            print("void send_" + name + "(portal *p, latency l");
            for (int j = 0; j < params.length; j++) {
                print(", " + params[j] + " p" + j);
            }
            print(") {");
            pos += TAB_SIZE;
            newLine();
            print(pname + " q = malloc(sizeof(_" + pname + "));");
            for (int j = 0; j < params.length; j++) {
                newLine();
                print("q->p" + j + " = p" + j + ";");
            }
            newLine();
            print("send_message(p, " + i + ", l, q);");
            pos -= TAB_SIZE;
        }        
    }

    // ----------------------------------------------------------------------
    // METHODS AND FIELDS
    // ----------------------------------------------------------------------

    /**
     * prints a field declaration
     */
    public void visitFieldDeclaration(JFieldDeclaration self,
                                      int modifiers,
                                      CType type,
                                      String ident,
                                      JExpression expr) {
        /*
          if (ident.indexOf("$") != -1) {
          return; // dont print generated elements
          }
        */

        newLine();
        // print(CModifier.toString(modifiers));
        print(type);
        print(" ");
        print(ident);
        if (expr != null) {
            print("\t= ");
            expr.accept(this);
        }
        print(";");
    }

    /**
     * prints a method declaration
     */
    public void visitMethodDeclaration(JMethodDeclaration self,
                                       int modifiers,
                                       CType returnType,
                                       String ident,
                                       JFormalParameter[] parameters,
                                       CClassType[] exceptions,
                                       JBlock body) {
        /*
          if (ident.equals(JAV_STATIC_INIT) || ident.equals(JAV_INIT)) {
          return; // we do not want to generate this methods in source code
          }
        */

        newLine();
        // print(CModifier.toString(modifiers));
        print(returnType);
        print(" ");
        print(ident);
        print("(");
        int count = 0;

        for (int i = 0; i < parameters.length; i++) {
            if (count != 0) {
                print(", ");
            }

            // if (!parameters[i].isGenerated()) {
            parameters[i].accept(this);
            count++;
            // }
        }
        print(")");

        if (declOnly)
        {
            print(";");
            return;
        }

        print(" ");
        if (body != null) {
            body.accept(this);
        } else {
            print(";");
        }
        newLine();
    }

    /**
     * prints a method declaration
     */
    public void visitConstructorDeclaration(JConstructorDeclaration self,
                                            int modifiers,
                                            String ident,
                                            JFormalParameter[] parameters,
                                            CClassType[] exceptions,
                                            JConstructorBlock body)
    {
        newLine();
        print(CModifier.toString(modifiers));
        print(ident);
        print("_");
        print(ident);
        print("(");
        int count = 0;
        for (int i = 0; i < parameters.length; i++) {
            if (count != 0) {
                print(", ");
            }
            if (!parameters[i].isGenerated()) {
                parameters[i].accept(this);
                count++;
            }
        }
        print(")");
        /*
          for (int i = 0; i < exceptions.length; i++) {
          if (i != 0) {
          print(", ");
          } else {
          print(" throws ");
          }
          print(exceptions[i].toString());
          }
        */
        print(" ");
        body.accept(this);
        newLine();
    }

    // ----------------------------------------------------------------------
    // STATEMENT
    // ----------------------------------------------------------------------

    /**
     * prints a while statement
     */
    public void visitWhileStatement(JWhileStatement self,
                                    JExpression cond,
                                    JStatement body) {
        print("while (");
        cond.accept(this);
        print(") ");

        body.accept(this);
    }

    /**
     * prints a variable declaration statement
     */
    public void visitVariableDeclarationStatement(JVariableDeclarationStatement self,
                                                  JVariableDefinition[] vars) {
        for (int i = 0; i < vars.length; i++) {
            vars[i].accept(this);
        }
    }

    /**
     * prints a variable declaration statement
     */
    public void visitVariableDefinition(JVariableDefinition self,
                                        int modifiers,
                                        CType type,
                                        String ident,
                                        JExpression expr) {
        print(CModifier.toString(modifiers));
        print(type);
        print(" ");
        print(ident);
        if (expr != null) {
            print(" = ");
            expr.accept(this);
        }
        print(";");
    }

    /**
     * prints a try-catch statement
     */
    public void visitTryCatchStatement(JTryCatchStatement self,
                                       JBlock tryClause,
                                       JCatchClause[] catchClauses) {
        print("try ");
        tryClause.accept(this);
        for (int i = 0; i < catchClauses.length; i++) {
            catchClauses[i].accept(this);
        }
    }

    /**
     * prints a try-finally statement
     */
    public void visitTryFinallyStatement(JTryFinallyStatement self,
                                         JBlock tryClause,
                                         JBlock finallyClause) {
        print("try ");
        tryClause.accept(this);
        if (finallyClause != null) {
            print(" finally ");
            finallyClause.accept(this);
        }
    }

    /**
     * prints a throw statement
     */
    public void visitThrowStatement(JThrowStatement self,
                                    JExpression expr) {
        print("throw ");
        expr.accept(this);
        print(";");
    }

    /**
     * prints a synchronized statement
     */
    public void visitSynchronizedStatement(JSynchronizedStatement self,
                                           JExpression cond,
                                           JStatement body) {
        print("synchronized (");
        cond.accept(this);
        print(") ");
        body.accept(this);
    }

    /**
     * prints a switch statement
     */
    public void visitSwitchStatement(JSwitchStatement self,
                                     JExpression expr,
                                     JSwitchGroup[] body) {
        print("switch (");
        expr.accept(this);
        print(") {");
        for (int i = 0; i < body.length; i++) {
            body[i].accept(this);
        }
        newLine();
        print("}");
    }

    /**
     * prints a return statement
     */
    public void visitReturnStatement(JReturnStatement self,
                                     JExpression expr) {
        print("return");
        if (expr != null) {
            print(" ");
            expr.accept(this);
        }
        print(";");
    }

    /**
     * prints a labeled statement
     */
    public void visitLabeledStatement(JLabeledStatement self,
                                      String label,
                                      JStatement stmt) {
        print(label + ":");
        stmt.accept(this);
    }

    /**
     * prints a if statement
     */
    public void visitIfStatement(JIfStatement self,
                                 JExpression cond,
                                 JStatement thenClause,
                                 JStatement elseClause) {
        print("if (");
        cond.accept(this);
        print(") ");
        pos += thenClause instanceof JBlock ? 0 : TAB_SIZE;
        thenClause.accept(this);
        pos -= thenClause instanceof JBlock ? 0 : TAB_SIZE;
        if (elseClause != null) {
            if ((elseClause instanceof JBlock) || (elseClause instanceof JIfStatement)) {
                print(" ");
            } else {
                newLine();
            }
            print("else ");
            pos += elseClause instanceof JBlock || elseClause instanceof JIfStatement ? 0 : TAB_SIZE;
            elseClause.accept(this);
            pos -= elseClause instanceof JBlock || elseClause instanceof JIfStatement ? 0 : TAB_SIZE;
        }
    }

    /**
     * prints a for statement
     */
    public void visitForStatement(JForStatement self,
                                  JStatement init,
                                  JExpression cond,
                                  JStatement incr,
                                  JStatement body) {
        print("for (");
        forInit = true;
        if (init != null) {
            init.accept(this);
        } else {
            print(";");
        }
        forInit = false;

        print(" ");
        if (cond != null) {
            cond.accept(this);
        }
        print("; ");

        if (incr != null) {
            incr.accept(this);
        }
        print(") ");

        print("{");
        pos += TAB_SIZE;
        body.accept(this);
        pos -= TAB_SIZE;
        newLine();
        print("}");
    }

    /**
     * prints a compound statement
     */
    public void visitCompoundStatement(JCompoundStatement self,
                                       JStatement[] body) {
        visitCompoundStatement(body);
    }

    /**
     * prints a compound statement
     */
    public void visitCompoundStatement(JStatement[] body) {
        for (int i = 0; i < body.length; i++) {
            if (body[i] instanceof JIfStatement &&
                i < body.length - 1 &&
                !(body[i + 1] instanceof JReturnStatement)) {
                newLine();
            }
            if (body[i] instanceof JReturnStatement && i > 0) {
                newLine();
            }

            newLine();
            body[i].accept(this);

            if (body[i] instanceof JVariableDeclarationStatement &&
                i < body.length - 1 &&
                !(body[i + 1] instanceof JVariableDeclarationStatement)) {
                newLine();
            }
        }
    }

    /**
     * prints an expression statement
     */
    public void visitExpressionStatement(JExpressionStatement self,
                                         JExpression expr) {
        expr.accept(this);
        if (!forInit) {
            print(";");
        }
    }

    /**
     * prints an expression list statement
     */
    public void visitExpressionListStatement(JExpressionListStatement self,
                                             JExpression[] expr) {
        for (int i = 0; i < expr.length; i++) {
            if (i != 0) {
                print(", ");
            }
            expr[i].accept(this);
        }
        if (forInit) {
            print(";");
        }
    }

    /**
     * prints a empty statement
     */
    public void visitEmptyStatement(JEmptyStatement self) {
        newLine();
        print(";");
    }

    /**
     * prints a do statement
     */
    public void visitDoStatement(JDoStatement self,
                                 JExpression cond,
                                 JStatement body) {
        newLine();
        print("do ");
        body.accept(this);
        print("");
        print("while (");
        cond.accept(this);
        print(");");
    }

    /**
     * prints a continue statement
     */
    public void visitContinueStatement(JContinueStatement self,
                                       String label) {
        newLine();
        print("continue");
        if (label != null) {
            print(" " + label);
        }
        print(";");
    }

    /**
     * prints a break statement
     */
    public void visitBreakStatement(JBreakStatement self,
                                    String label) {
        newLine();
        print("break");
        if (label != null) {
            print(" " + label);
        }
        print(";");
    }

    /**
     * prints an expression statement
     */
    public void visitBlockStatement(JBlock self,
                                    JStatement[] body,
                                    JavaStyleComment[] comments) {
        print("{");
        pos += TAB_SIZE;
        visitCompoundStatement(body);
        if (comments != null) {
            visitComments(comments);
        }
        pos -= TAB_SIZE;
        newLine();
        print("}");
    }

    /**
     * prints a type declaration statement
     */
    public void visitTypeDeclarationStatement(JTypeDeclarationStatement self,
                                              JTypeDeclaration decl) {
        decl.accept(this);
    }

    // ----------------------------------------------------------------------
    // EXPRESSION
    // ----------------------------------------------------------------------

    /**
     * prints an unary plus expression
     */
    public void visitUnaryPlusExpression(JUnaryExpression self,
                                         JExpression expr)
    {
        print("+");
        expr.accept(this);
    }

    /**
     * prints an unary minus expression
     */
    public void visitUnaryMinusExpression(JUnaryExpression self,
                                          JExpression expr)
    {
        print("-");
        expr.accept(this);
    }

    /**
     * prints a bitwise complement expression
     */
    public void visitBitwiseComplementExpression(JUnaryExpression self,
						 JExpression expr)
    {
        print("~");
        expr.accept(this);
    }

    /**
     * prints a logical complement expression
     */
    public void visitLogicalComplementExpression(JUnaryExpression self,
						 JExpression expr)
    {
        print("!");
        expr.accept(this);
    }

    /**
     * prints a type name expression
     */
    public void visitTypeNameExpression(JTypeNameExpression self,
                                        CType type) {
        print(type);
    }

    /**
     * prints a this expression
     */
    public void visitThisExpression(JThisExpression self,
                                    JExpression prefix) {
        if (prefix != null) {
            prefix.accept(this);
            print(".this");
        } else {
            print("this");
        }
    }

    /**
     * prints a super expression
     */
    public void visitSuperExpression(JSuperExpression self) {
        print("super");
    }

    /**
     * prints a shift expression
     */
    public void visitShiftExpression(JShiftExpression self,
                                     int oper,
                                     JExpression left,
                                     JExpression right) {
        left.accept(this);
        if (oper == OPE_SL) {
            print(" << ");
        } else if (oper == OPE_SR) {
            print(" >> ");
        } else {
            print(" >>> ");
        }
        right.accept(this);
    }

    /**
     * prints a shift expressiona
     */
    public void visitRelationalExpression(JRelationalExpression self,
                                          int oper,
                                          JExpression left,
                                          JExpression right) {
        left.accept(this);
        switch (oper) {
        case OPE_LT:
            print(" < ");
            break;
        case OPE_LE:
            print(" <= ");
            break;
        case OPE_GT:
            print(" > ");
            break;
        case OPE_GE:
            print(" >= ");
            break;
        default:
            throw new InconsistencyException();
        }
        right.accept(this);
    }

    /**
     * prints a prefix expression
     */
    public void visitPrefixExpression(JPrefixExpression self,
                                      int oper,
                                      JExpression expr) {
        if (oper == OPE_PREINC) {
            print("++");
        } else {
            print("--");
        }
        expr.accept(this);
    }

    /**
     * prints a postfix expression
     */
    public void visitPostfixExpression(JPostfixExpression self,
                                       int oper,
                                       JExpression expr) {
        expr.accept(this);
        if (oper == OPE_POSTINC) {
            print("++");
        } else {
            print("--");
        }
    }

    /**
     * prints a parenthesed expression
     */
    public void visitParenthesedExpression(JParenthesedExpression self,
                                           JExpression expr) {
        print("(");
        expr.accept(this);
        print(")");
    }

    /**
     * Prints an unqualified anonymous class instance creation expression.
     */
    public void visitQualifiedAnonymousCreation(JQualifiedAnonymousCreation self,
                                                JExpression prefix,
                                                String ident,
                                                JExpression[] params,
                                                JClassDeclaration decl)
    {
        prefix.accept(this);
        print(".new " + ident + "(");
        visitArgs(params, 0);
        print(")");
        // decl.genInnerJavaCode(this);
    }

    /**
     * Prints an unqualified instance creation expression.
     */
    public void visitQualifiedInstanceCreation(JQualifiedInstanceCreation self,
                                               JExpression prefix,
                                               String ident,
                                               JExpression[] params)
    {
        prefix.accept(this);
        print(".new " + ident + "(");
        visitArgs(params, 0);
        print(")");
    }

    /**
     * Prints an unqualified anonymous class instance creation expression.
     */
    public void visitUnqualifiedAnonymousCreation(JUnqualifiedAnonymousCreation self,
                                                  CClassType type,
                                                  JExpression[] params,
                                                  JClassDeclaration decl)
    {
        print("new " + type + "(");
        visitArgs(params, 0);
        print(")");
        // decl.genInnerJavaCode(this);
    }

    /**
     * Prints an unqualified instance creation expression.
     */
    public void visitUnqualifiedInstanceCreation(JUnqualifiedInstanceCreation self,
                                                 CClassType type,
                                                 JExpression[] params)
    {
        print("new " + type + "(");
        visitArgs(params, 0);
        print(")");
    }

    /**
     * prints an array allocator expression
     */
    public void visitNewArrayExpression(JNewArrayExpression self,
                                        CType type,
                                        JExpression[] dims,
                                        JArrayInitializer init)
    {
        print("new ");
        print(type);
        for (int i = 0; i < dims.length; i++) {
            print("[");
            if (dims[i] != null) {
                dims[i].accept(this);
            }
            print("]");
        }
        if (init != null) {
            init.accept(this);
        }
    }

    /**
     * prints a name expression
     */
    public void visitNameExpression(JNameExpression self,
                                    JExpression prefix,
                                    String ident) {
        if (prefix != null) {
            prefix.accept(this);
            print("->");
        }
        print(ident);
    }

    /**
     * prints an array allocator expression
     */
    public void visitBinaryExpression(JBinaryExpression self,
                                      String oper,
                                      JExpression left,
                                      JExpression right) {
        left.accept(this);
        print(" ");
        print(oper);
        print(" ");
        right.accept(this);
    }

    /**
     * prints a method call expression
     */
    public void visitMethodCallExpression(JMethodCallExpression self,
                                          JExpression prefix,
                                          String ident,
                                          JExpression[] args) {
        /*
          if (ident != null && ident.equals(JAV_INIT)) {
          return; // we do not want generated methods in source code
          }
        */

        print(ident);
        print("(");
        int i = 0;
        if (prefix != null) {
            prefix.accept(this);
            i++;
        }
        visitArgs(args, i);
        print(")");
    }

    /**
     * prints a local variable expression
     */
    public void visitLocalVariableExpression(JLocalVariableExpression self,
                                             String ident) {
        print(ident);
    }

    /**
     * prints an instanceof expression
     */
    public void visitInstanceofExpression(JInstanceofExpression self,
                                          JExpression expr,
                                          CType dest) {
        expr.accept(this);
        print(" instanceof ");
        print(dest);
    }

    /**
     * prints an equality expression
     */
    public void visitEqualityExpression(JEqualityExpression self,
                                        boolean equal,
                                        JExpression left,
                                        JExpression right) {
        left.accept(this);
        print(equal ? " == " : " != ");
        right.accept(this);
    }

    /**
     * prints a conditional expression
     */
    public void visitConditionalExpression(JConditionalExpression self,
                                           JExpression cond,
                                           JExpression left,
                                           JExpression right) {
        cond.accept(this);
        print(" ? ");
        left.accept(this);
        print(" : ");
        right.accept(this);
    }

    /**
     * prints a compound expression
     */
    public void visitCompoundAssignmentExpression(JCompoundAssignmentExpression self,
                                                  int oper,
                                                  JExpression left,
                                                  JExpression right) {
        left.accept(this);
        switch (oper) {
        case OPE_STAR:
            print(" *= ");
            break;
        case OPE_SLASH:
            print(" /= ");
            break;
        case OPE_PERCENT:
            print(" %= ");
            break;
        case OPE_PLUS:
            print(" += ");
            break;
        case OPE_MINUS:
            print(" -= ");
            break;
        case OPE_SL:
            print(" <<= ");
            break;
        case OPE_SR:
            print(" >>= ");
            break;
        case OPE_BSR:
            print(" >>>= ");
            break;
        case OPE_BAND:
            print(" &= ");
            break;
        case OPE_BXOR:
            print(" ^= ");
            break;
        case OPE_BOR:
            print(" |= ");
            break;
        }
        right.accept(this);
    }

    /**
     * prints a field expression
     */
    public void visitFieldExpression(JFieldAccessExpression self,
                                     JExpression left,
                                     String ident)
    {
        if (ident.equals(JAV_OUTER_THIS)) {// don't generate generated fields
            print(left.getType().getCClass().getOwner().getType() + "->this");
            return;
        }
        int		index = ident.indexOf("_$");
        if (index != -1) {
            print(ident.substring(0, index));      // local var
        } else {
            left.accept(this);
            print("->" + ident);
        }
    }

    /**
     * prints a class expression
     */
    public void visitClassExpression(JClassExpression self, CType type) {
        print(type);
        print(".class");
    }

    /**
     * prints a cast expression
     */
    public void visitCastExpression(JCastExpression self,
                                    JExpression expr,
                                    CType type)
    {
        print("(");
        print(type);
        print(")");
        expr.accept(this);
    }

    /**
     * prints a cast expression
     */
    public void visitUnaryPromoteExpression(JUnaryPromote self,
                                            JExpression expr,
                                            CType type)
    {
        print("(");
        print(type);
        print(")");
        print("(");
        expr.accept(this);
        print(")");
    }

    /**
     * prints a compound assignment expression
     */
    public void visitBitwiseExpression(JBitwiseExpression self,
                                       int oper,
                                       JExpression left,
                                       JExpression right) {
        left.accept(this);
        switch (oper) {
        case OPE_BAND:
            print(" & ");
            break;
        case OPE_BOR:
            print(" | ");
            break;
        case OPE_BXOR:
            print(" ^ ");
            break;
        default:
            throw new InconsistencyException();
        }
        right.accept(this);
    }

    /**
     * prints an assignment expression
     */
    public void visitAssignmentExpression(JAssignmentExpression self,
                                          JExpression left,
                                          JExpression right) {
        /*
          if ((left instanceof JFieldAccessExpression) &&
          ((JFieldAccessExpression)left).getField().getIdent().equals(Constants.JAV_OUTER_THIS)) {
          return;
          }
        */

        left.accept(this);
        print(" = ");
        right.accept(this);
    }

    /**
     * prints an array length expression
     */
    public void visitArrayLengthExpression(JArrayLengthExpression self,
                                           JExpression prefix) {
        prefix.accept(this);
        print(".length");
    }

    /**
     * prints an array length expression
     */
    public void visitArrayAccessExpression(JArrayAccessExpression self,
                                           JExpression prefix,
                                           JExpression accessor) {
        prefix.accept(this);
        print("[");
        accessor.accept(this);
        print("]");
    }

    /**
     * prints an array length expression
     */
    public void visitComments(JavaStyleComment[] comments) {
        for (int i = 0; i < comments.length; i++) {
            if (comments[i] != null) {
                visitComment(comments[i]);
            }
        }
    }

    /**
     * prints an array length expression
     */
    public void visitComment(JavaStyleComment comment) {
        StringTokenizer	tok = new StringTokenizer(comment.getText(), "\n");

        if (comment.hadSpaceBefore()) {
            newLine();
        }

        if (comment.isLineComment()) {
            print("// " + tok.nextToken().trim());
            p.println();
        } else {
            if (p.getLine() > 0) {
                if (!nl) {
                    newLine();
                }
                newLine();
            }
            print("/*");
            while (tok.hasMoreTokens()){
                String comm = tok.nextToken().trim();
                if (comm.startsWith("*")) {
                    comm = comm.substring(1).trim();
                }
                if (tok.hasMoreTokens() || comm.length() > 0) {
                    newLine();
                    print(" * " + comm);
                }
            }
            newLine();
            print(" */");
            newLine();
        }

        if (comment.hadSpaceAfter()) {
            newLine();
        }
    }

    /**
     * prints an array length expression
     */
    public void visitJavadoc(JavadocComment comment) {
        StringTokenizer	tok = new StringTokenizer(comment.getText(), "\n");
        boolean		isFirst = true;

        if (!nl) {
            newLine();
        }
        newLine();
        print("/**");
        while (tok.hasMoreTokens()) {
            String	text = tok.nextToken().trim();
            String	type = null;
            boolean	param = false;
            int	idx = text.indexOf("@param");
            if (idx >= 0) {
                type = "@param";
                param = true;
            }
            if (idx < 0) {
                idx = text.indexOf("@exception");
                if (idx >= 0) {
                    type = "@exception";
                    param = true;
                }
            }
            if (idx < 0) {
                idx = text.indexOf("@exception");
                if (idx >= 0) {
                    type = "@exception";
                    param = true;
                }
            }
            if (idx < 0) {
                idx = text.indexOf("@author");
                if (idx >= 0) {
                    type = "@author";
                }
            }
            if (idx < 0) {
                idx = text.indexOf("@see");
                if (idx >= 0) {
                    type = "@see";
                }
            }
            if (idx < 0) {
                idx = text.indexOf("@version");
                if (idx >= 0) {
                    type = "@version";
                }
            }
            if (idx < 0) {
                idx = text.indexOf("@return");
                if (idx >= 0) {
                    type = "@return";
                }
            }
            if (idx < 0) {
                idx = text.indexOf("@deprecated");
                if (idx >= 0) {
                    type = "@deprecated";
                }
            }
            if (idx >= 0) {
                newLine();
                isFirst = false;
                if (param) {
                    text = text.substring(idx + type.length()).trim();
                    idx = Math.min(text.indexOf(" ") == -1 ? Integer.MAX_VALUE : text.indexOf(" "),
                                   text.indexOf("\t") == -1 ? Integer.MAX_VALUE : text.indexOf("\t"));
                    if (idx == Integer.MAX_VALUE) {
                        idx = 0;
                    }
                    String	before = text.substring(0, idx);
                    print(" * " + type);
                    pos += 12;
                    print(before);
                    pos += 20;
                    print(text.substring(idx).trim());
                    pos -= 20;
                    pos -= 12;
                } else {
                    text = text.substring(idx + type.length()).trim();
                    print(" * " + type);
                    pos += 12;
                    print(text);
                    pos -= 12;
                }
            } else {
                text = text.substring(text.indexOf("*") + 1);
                if (tok.hasMoreTokens() || text.length() > 0) {
                    newLine();
                    print(" * ");
                    pos += isFirst ? 0 : 32;
                    print(text.trim());
                    pos -= isFirst ? 0 : 32;
                }
            }
        }
        newLine();
        print(" */");
    }

    // ----------------------------------------------------------------------
    // STREAMIT IR HANDLERS
    // ----------------------------------------------------------------------

    public void visitCreatePortalExpression() {
        print("create_portal()");
    }

    public void visitInitStatement(SIRInitStatement self,
                                   JExpression[] body,
                                   SIRStream stream)
    {
        print("/* InitStatement */");
    }
    
    public void visitLatency(SIRLatency self)
    {
        print("/* Latency */");
    }
    
    public void visitLatencyMax(SIRLatencyMax self)
    {
        print("/* LatencyMax */");
    }
    
    public void visitLatencyRange(SIRLatencyRange self)
    {
        print("/* LatencyRange */");
    }
    
    public void visitLatencySet(SIRLatencySet self)
    {
        print("/* LatencySet */");
    }

    public void visitMessageStatement(SIRMessageStatement self,
                                      JExpression portal,
                                      String iname,
                                      String ident,
                                      JExpression[] params,
                                      SIRLatency latency)
    {
	print("send_" + iname + "_" + ident + "(" + portal + ", ");
        latency.accept(this);
        if (params != null)
            for (int i = 0; i < params.length; i++)
                if (params[i] != null)
                {
                    print(", ");
                    params[i].accept(this);
                }
        print(");");
    }

    public void visitPeekExpression(SIRPeekExpression self,
                                    CType tapeType,
                                    JExpression num)
    {
        print("PEEK(data->context, ");
        if (tapeType != null)
            print(tapeType);
        else
            print("/* null tapeType! */ int");
        print(", ");
        num.accept(this);
        print(")");
    }
    
    public void visitPopExpression(SIRPopExpression self,
                                   CType tapeType)
    {
        print("POP(data->context, ");
        if (tapeType != null)
            print(tapeType);
        else
            print("/* null tapeType! */ int");
        print(")");
    }
    
    public void visitPrintStatement(SIRPrintStatement self,
                                    JExpression exp)
    {
        CType type = exp.getType();
        
        if (type.equals(CStdType.Boolean))
        {
            print("printf(\"%s\\n\", ");
            exp.accept(this);
            print(" ? \"true\" : \"false\");");
        }
        else if (type.equals(CStdType.Byte) ||
                 type.equals(CStdType.Integer) ||
                 type.equals(CStdType.Short))
        {
            print("printf(\"%d\\n\", ");
            exp.accept(this);
            print(");");
        }
        else if (type.equals(CStdType.Char))
        {
            print("printf(\"%c\\n\", ");
            exp.accept(this);
            print(");");
        }
        else if (type.equals(CStdType.Float) ||
                 type.equals(CStdType.Double))
        {
            print("printf(\"%f\\n\", ");
            exp.accept(this);
            print(");");
        }
        else if (type.equals(CStdType.Long))
        {
            print("printf(\"%ld\\n\", ");
            exp.accept(this);
            print(");");
        }
        else
        {
            print("printf(\"(unprintable type)\\n\", ");
            exp.accept(this);
            print(");");
        }
    }
    
    public void visitPushExpression(SIRPushExpression self,
                                    CType tapeType,
                                    JExpression val)
    {
        print("PUSH(data->context, ");
        if (tapeType != null)
            print(tapeType);
        else
            print("/* null tapeType! */ int");
        print(", ");
        val.accept(this);
        print(")");
    }
    
    public void visitRegReceiverStatement(SIRRegReceiverStatement self,
                                          JExpression portal,
					  SIRStream receiver, 
					  JMethodDeclaration[] methods)
    {
	/*        print("register_receiver(this->context, ");
        print(fn);
        print(");");*/
    }
    
    public void visitRegSenderStatement(SIRRegSenderStatement self,
                                        String fn,
                                        SIRLatency latency)
    {
        print("register_sender(this->context, ");
        print(fn);
        print(", ");
        latency.accept(this);
        print(");");
    }

    public void visitSetChild(LIRSetChild self,
                              JExpression streamContext,
                              String childType,
                              String childName)
    {
        // Pay attention, three statements!
        print("data->" + childName + " = malloc(sizeof(_" + childType + "));");
        newLine();
        print("data->" + childName + "->context = " +
              "create_context(data->" + childName + ");");
        newLine();
        print("register_child(");
        streamContext.accept(this);
        print(", data->" + childName + "->context);");
    }
    
    public void visitSetTape(LIRSetTape self,
                             JExpression streamContext,
                             JExpression srcStruct,
                             JExpression dstStruct,
                             CType type,
                             int size)
    {
        print("create_tape(");
        srcStruct.accept(this);
        print("->context, ");
        dstStruct.accept(this);
        print("->context, sizeof(" + type + "), " + size + ");");
    }
    
    /**
     * Visits a function pointer.
     */
    public void visitFunctionPointer(LIRFunctionPointer self,
                                     String name)
    {
        // This is an expression.
        print(name);
    }
    
    /**
     * Visits an LIR node.
     */
    public void visitNode(LIRNode self)
    {
        // This should never be called directly.
        print("/* Unexpected visitNode */");
    }

    /**
     * Visits a child registration node.
     */
    public void visitSetChild(LIRSetChild self,
                              JExpression streamContext,
                              JExpression childContext)
    {
        // This is a statement.
        newLine();
        print("register_child(");
        streamContext.accept(this);
        print(", ");
        childContext.accept(this);
        print(");");
    }
    
    /**
     * Visits a decoder registration node.
     */
    public void visitSetDecode(LIRSetDecode self,
                               JExpression streamContext,
                               LIRFunctionPointer fp)
    {
        print("set_decode(");
        streamContext.accept(this);
        print(", ");
        fp.accept(this);
        print(");");
    }

    /**
     * Visits a feedback loop delay node.
     */
    public void visitSetDelay(LIRSetDelay self,
                              JExpression data,
                              JExpression streamContext,
                              int delay,
                              CType type,
                              LIRFunctionPointer fp)
    {
        /* This doesn't work quite right yet, but it's closer. */
        print("FEEDBACK_DELAY(");
        data.accept(this);
        print(", ");
        streamContext.accept(this);
        print(", " + delay + ", " + type + ", ");
        fp.accept(this);
        print(");");
    }
    
    /**
     * Visits an encoder registration node.
     */
    public void visitSetEncode(LIRSetEncode self,
                        JExpression streamContext,
                        LIRFunctionPointer fp)
    {
        print("set_encode(");
        streamContext.accept(this);
        print(", ");
        fp.accept(this);
        print(");");
    }
    
    /**
     * Visits a joiner-setting node.
     */
    public void visitSetJoiner(LIRSetJoiner self,
                               JExpression streamContext,
                               SIRJoinType type,
                               int ways,
                               int[] weights)
    {
        print("set_joiner(");
        streamContext.accept(this);
        print(", " + type + ", " + String.valueOf(ways));
        if (weights != null)
        {
            for (int i = 0; i < weights.length; i++)
                print(", " + String.valueOf(weights[i]));
        }
        print(");");
    }

    /**
     * Visits a peek-rate-setting node.
     */
    public void visitSetPeek(LIRSetPeek self,
                      JExpression streamContext,
                      int peek)
    {
        print("set_peek(");
        streamContext.accept(this);
        print(", " + peek + ");");
    }
    
    /**
     * Visits a pop-rate-setting node.
     */
    public void visitSetPop(LIRSetPop self,
                     JExpression streamContext,
                     int pop)
    {
        print("set_pop(");
        streamContext.accept(this);
        print(", " + pop + ");");
    }
    
    /**
     * Visits a push-rate-setting node.
     */
    public void visitSetPush(LIRSetPush self,
                      JExpression streamContext,
                      int push)
    {
        print("set_push(");
        streamContext.accept(this);
        print(", " + push + ");");
    }

    /**
     * Visits a splitter-setting node.
     */
    public void visitSetSplitter(LIRSetSplitter self,
                                 JExpression streamContext,
                                 SIRSplitType type,
                                 int ways,
                                 int[] weights)
    {
        print("set_splitter(");
        streamContext.accept(this);
        print(", " + type + ", " + String.valueOf(ways));
        if (weights != null)
        {
            for (int i = 0; i < weights.length; i++)
                print(", " + String.valueOf(weights[i]));
        }
        print(");");
    }

    /**
     * Visits a stream-type-setting node.
     */
    public void visitSetStreamType(LIRSetStreamType self,
                            JExpression streamContext,
                            LIRStreamType streamType)
    {
        print("set_stream_type(");
        streamContext.accept(this);
        print(", " + streamType + ");");
    }
    
    /**
     * Visits a work-function-setting node.
     */
    public void visitSetWork(LIRSetWork self,
                      JExpression streamContext,
                      LIRFunctionPointer fn)
    {
        print("set_work(");
        streamContext.accept(this);
        print(", (work_fn)");
        fn.accept(this);
        print(");");
    }

    public void visitMainFunction(LIRMainFunction self,
                                  String typeName,
                                  LIRFunctionPointer init,
				  List initStatements)
    {
        print(typeName + " data = malloc(sizeof(_" + typeName + "));");
        newLine();
        print("data->context = create_context(data);");
        newLine();
        init.accept(this);
        print("(data);");
        newLine();
        print("connect_tapes(data->context);");
        newLine();
        Iterator iter = initStatements.iterator();
        while (iter.hasNext())
            ((JStatement)(iter.next())).accept(this);
        newLine();
        print("streamit_run(data->context);");
    }

    /**
     * Visits a set body of feedback loop.
     */
    public void visitSetBodyOfFeedback(LIRSetBodyOfFeedback self,
				       JExpression streamContext,
                                       JExpression childContext,
				       CType inputType,
				       CType outputType,
				       int inputSize,
				       int outputSize) {
        /* Three things need to happen for feedback loop children:
         * they need to be registered, their input tapes need
         * to be created, and the output tapes need to be created.
         * LIRSetChild deals with the registration, so we just need
         * to take care of the tapes.  For a feedback loop body,
         * we're looking at the output of the joiner and the
         * input of the splitter. */
        print("create_splitjoin_tape(");
        streamContext.accept(this);
        print(", JOINER, OUTPUT, 0, ");
        childContext.accept(this);
        print(", sizeof(" + inputType + "), " + inputSize + ");");
        newLine();
        print("create_splitjoin_tape(");
        streamContext.accept(this);
        print(", SPLITTER, INPUT, 0, ");
        childContext.accept(this);
        print(", sizeof(" + outputType + "), " + outputSize + ");");
    }

    /**
     * Visits a set loop of feedback loop.
     */
    public void visitSetLoopOfFeedback(LIRSetLoopOfFeedback self,
				       JExpression streamContext,
                                       JExpression childContext,
				       CType inputType,
				       CType outputType,
				       int inputSize,
				       int outputSize) {
        /* The loop's output goes to input 1 of the joiner, and its
         * input comes from output 1 of the splitter.  (input/output
         * 0 are connected to the outside of the block.) */
        print("create_splitjoin_tape(");
        streamContext.accept(this);
        print(", SPLITTER, OUTPUT, 1, ");
        childContext.accept(this);
        print(", sizeof(" + inputType + "), " + inputSize + ");");
        newLine();
        print("create_splitjoin_tape(");
        streamContext.accept(this);
        print(", JOINER, INPUT, 1, ");
        childContext.accept(this);
        print(", sizeof(" + outputType + "), " + outputSize + ");");
    }

    /**
     * Visits a set a parallel stream.
     */
    public void visitSetParallelStream(LIRSetParallelStream self,
				       JExpression streamContext,
                                       JExpression childContext,
				       int position,
				       CType inputType,
				       CType outputType,
				       int inputSize,
				       int outputSize) {
        /* For  split/joins now.  Again, assume registration has
         * already happened; we just need to connect tapes.
         * Use the position'th slot on the splitter output and
         * joiner input. */
        print("create_splitjoin_tape(");
        streamContext.accept(this);
        print(", SPLITTER, OUTPUT, " + position + ", ");
        childContext.accept(this);
        print(", sizeof(" + inputType + "), " + inputSize + ");");
        newLine();
        print("create_splitjoin_tape(");
        streamContext.accept(this);
        print(", JOINER, INPUT, " + position + ", ");
        childContext.accept(this);
        print(", sizeof(" + outputType + "), " + outputSize + ");");
    }


    // ----------------------------------------------------------------------
    // UTILS
    // ----------------------------------------------------------------------

    /**
     * prints an array length expression
     */
    public void visitSwitchLabel(JSwitchLabel self,
                                 JExpression expr) {
        newLine();
        if (expr != null) {
            print("case ");
            expr.accept(this);
            print(": ");
        } else {
            print("default: ");
        }
    }

    /**
     * prints an array length expression
     */
    public void visitSwitchGroup(JSwitchGroup self,
                                 JSwitchLabel[] labels,
                                 JStatement[] stmts) {
        for (int i = 0; i < labels.length; i++) {
            labels[i].accept(this);
        }
        pos += TAB_SIZE;
        for (int i = 0; i < stmts.length; i++) {
            newLine();
            stmts[i].accept(this);
        }
        pos -= TAB_SIZE;
    }

    /**
     * prints an array length expression
     */
    public void visitCatchClause(JCatchClause self,
                                 JFormalParameter exception,
                                 JBlock body) {
        print(" catch (");
        exception.accept(this);
        print(") ");
        body.accept(this);
    }

    /**
     * prints a boolean literal
     */
    public void visitBooleanLiteral(boolean value) {
        print(value);
    }

    /**
     * prints a byte literal
     */
    public void visitByteLiteral(byte value) {
        print("(byte)" + value);
    }

    /**
     * prints a character literal
     */
    public void visitCharLiteral(char value) {
        switch (value) {
        case '\b':
            print("'\\b'");
            break;
        case '\r':
            print("'\\r'");
            break;
        case '\t':
            print("'\\t'");
            break;
        case '\n':
            print("'\\n'");
            break;
        case '\f':
            print("'\\f'");
            break;
        case '\\':
            print("'\\\\'");
            break;
        case '\'':
            print("'\\''");
            break;
        case '\"':
            print("'\\\"'");
            break;
        default:
            print("'" + value + "'");
        }
    }

    /**
     * prints a double literal
     */
    public void visitDoubleLiteral(double value) {
        print("(double)" + value);
    }

    /**
     * prints a float literal
     */
    public void visitFloatLiteral(float value) {
        print(value + "F");
    }

    /**
     * prints a int literal
     */
    public void visitIntLiteral(int value) {
        print(value);
    }

    /**
     * prints a long literal
     */
    public void visitLongLiteral(long value) {
        print(value + "L");
    }

    /**
     * prints a short literal
     */
    public void visitShortLiteral(short value) {
        print("(short)" + value);
    }

    /**
     * prints a string literal
     */
    public void visitStringLiteral(String value) {
        print('"' + value + '"');
    }

    /**
     * prints a null literal
     */
    public void visitNullLiteral() {
        print("null");
    }

    /**
     * prints an array length expression
     */
    public void visitPackageName(String name) {
        // print("package " + name + ";");
        // newLine();
    }

    /**
     * prints an array length expression
     */
    public void visitPackageImport(String name) {
        // print("import " + name.replace('/', '.') + ".*;");
    }

    /**
     * prints an array length expression
     */
    public void visitClassImport(String name) {
        // print("import " + name.replace('/', '.') + ";");
    }

    /**
     * prints an array length expression
     */
    public void visitFormalParameters(JFormalParameter self,
                                      boolean isFinal,
                                      CType type,
                                      String ident) {
        if (isFinal) {
            print("final ");
        }
        print(type.toString());
        if (ident.indexOf("$") == -1) {
            print(" ");
            print(ident);
        }
    }

    /**
     * prints an array length expression
     */
    public void visitArgs(JExpression[] args, int base) {
        if (args != null) {
            for (int i = 0; i < args.length; i++) {
                if (i + base != 0) {
                    print(", ");
                }
                args[i].accept(this);
            }
        }
    }

    /**
     * prints an array length expression
     */
    public void visitConstructorCall(JConstructorCall self,
                                     boolean functorIsThis,
                                     JExpression[] params)
    {
        newLine();
        print(functorIsThis ? "this" : "super");
        print("(");
        visitArgs(params, 0);
        print(");");
    }

    /**
     * prints an array initializer expression
     */
    public void visitArrayInitializer(JArrayInitializer self,
                                      JExpression[] elems)
    {
        newLine();
        print("{");
        for (int i = 0; i < elems.length; i++) {
            if (i != 0) {
                print(", ");
            }
            elems[i].accept(this);
        }
        print("}");
    }

    
    // ----------------------------------------------------------------------
    // PROTECTED METHODS
    // ----------------------------------------------------------------------

    protected void newLine() {
        p.println();
    }

    protected void print(Object s) {
        print(s.toString());
    }

    protected void print(String s) {
        p.setPos(pos);
        p.print(s);
    }

    protected void print(boolean s) {
        print("" + s);
    }

    protected void print(int s) {
        print("" + s);
    }

    protected void print(char s) {
        print("" + s);
    }

    protected void print(double s) {
        print("" + s);
    }

    // ----------------------------------------------------------------------
    // DATA MEMBERS
    // ----------------------------------------------------------------------

    protected boolean			forInit;	// is on a for init
    protected int				TAB_SIZE = 2;
    protected int				WIDTH = 80;
    protected int				pos;
    protected String                      className;

    protected TabbedPrintWriter		p;
    protected StringWriter                str;
    protected boolean			nl = true;
    protected boolean                   declOnly = false;
}
