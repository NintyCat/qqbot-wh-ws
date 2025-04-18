# qqbot-wh-ws
 
QQ官方机器人webhook转websocket的工具

签证使用了qqbot_webhook项目

参考qbot-webhook-to-websocket项目，做了一层分发，可以根据群组ID分发消息，可用于区分测试环境和线上环境

使用方法：

1、回调地址(填写你自己的地址和secret，需已备案有证书，部分野鸡证书无效)：

https://www.abcdefg.com/webhook?secret=xxxxxxxxxxxxxxxxxxx

![image](https://github.com/user-attachments/assets/2a0ba6b8-e3b6-4239-afd1-7c8859605a82)

2、机器人socket连接地址，group-id为你需要单独分发的群组，不需要单独分发填0：

wss://www.abcdefg.com/ws/{secret}/{group-id}

如：

线上使用：wss://www.abcdefg.com/ws/{secret}/0

测试使用：wss://www.abcdefg.com/ws/{secret}/B7C7A625AEA8A14D770B2BD87FD99827

匹配到B7C7A625AEA8A14D770B2BD87FD99827的群组就会分发给测试环境，其余分发给0，都没有则不分发。


注意：我只需要群组，频道和好友没有做判断，有需要的自己改
