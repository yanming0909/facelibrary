package com.idl.stub.util;

public class ErrorResponse {
    public int error_code;
    public String error_msg;

    public boolean isSuccess(){
        return error_code<=0;
    }
}
