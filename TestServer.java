
import ServerCrossAndCircleGame.ServerGomokuGame;

/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */

/**
 *
 * @author noran
 */
public class TestServer {

    /**
     * @param args the command line arguments
     */
    public static void main(String[] args) {
        
        ServerGomokuGame x = new ServerGomokuGame(2000);
        new Thread(x).start();
        //ServerGomokuGame x = new ServerGomokuGame(2000);
        //x.startServer();
    }
    
}
