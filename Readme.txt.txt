HOW TO RUN:

Para correr o projeto, temos de ter 2 versões do projeto (ou seja, fazer uma cópia do projeto original)
e garantir que cada um deles tem uma porta diferente, ou seja, no P2PClient.java, o valor da
variável MESSAGE_PORT tem de ser diferente entre eles.

Em cada um, eliminar os seguintes ficheiros:
- registered_users.csv
- privateKey.txt
- publicKey.txt

Em apenas 1 deles, correr o server (P2PServer.java), depois no mesmo correr o App_Visual.java,
dar nome de utilizador (que é pedido pela GUI), ao carregar no botão para enviar o username, vai
ser criado os ficheiros que foram eliminados anteriormente. No outro, correr apenas novamente a
App_Visual.java, escolher o username e depois vai aparecer a lista dos users registados e podem
conversar entre eles se estiverem ambos ligados.