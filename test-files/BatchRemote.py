class BatchRemote :
    
    def Var(self, name) :
        return "(Var " + name + ")"
    
    def Data(self, x) :
        return "(Data " + str(x) + ")"
    
    def Prim(self, op, args) :
        return "(" + op + " " + args + ")"
    
    def Prop(self, base, field) :
        return "(Prop " + base + field + ")"
    
    def Assign(self, op, target, source) :
        return "(Assign " + op + target + source + ")"
    
    def Let(self, var, expression, body) :
        return "(Let " + var + "=" + expression + " in " + body + ")"
    
    def If(self, condition, thenExp, elseExp) :
        return "(If " + condition + " then " + thenExp + " else " + elseExp + ")"
    
    def Loop(self, var, collection, body) :
        return "(For " + var + " in " + collection + " do " + body + ")"
    
    def Call(self, target, method, args) :
        return "(Call " + target + " " + method + " " + args + ")"

