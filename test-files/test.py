from BatchRemote import BatchRemote

f = BatchRemote()
#print f.Data(1)
mybatch r in f :
    x = 2
    x + 2
    3
    print "Batch"
    print f.Data(5)
    while x :
        x = 0
        print "While"
    r.foo()
print "Hello World"
