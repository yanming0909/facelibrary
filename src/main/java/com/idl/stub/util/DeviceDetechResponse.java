package com.idl.stub.util;

import java.util.List;

public class DeviceDetechResponse extends ErrorResponse{
    public int person_num;
    public int driver_num;
    public long log_id;
    public List<PersonInfo> person_info;


    public class PersonInfo{
        public Location location;
        public Attributes attributes;

        public class Attributes{
            public Attr cellphone;                  //使用手机
            public Attr yawning;                    //打哈欠
            public Attr not_buckling_up;            //未带安全带
            public Attr no_face_mask;               //未带口罩
            public Attr both_hands_leaving_wheel;   //双手离开驾驶盘
            public Attr eyes_closed;                //闭眼
            public Attr head_lowered;               //低头
            public Attr smoke;                      //吸烟
            public Attr not_facing_front;           //视角未朝前方

            public class Attr{
                public float threshold;
                public double score;
            }
        }

        public class  Location{
            public int width;
            public int top;
            public double score;
            public int left;
            public int height;
        }

    }
}
