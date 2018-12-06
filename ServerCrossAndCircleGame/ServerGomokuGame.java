package ServerCrossAndCircleGame;

/*
 * Ariel Grabijas
 * Java multithreaded Gomoku game server.
 */

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;
import com.google.common.collect.HashBasedTable;
import com.google.gson.Gson;
import com.google.gson.JsonParser;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Map;
import java.util.Objects;
import java.util.SortedMap;
import java.util.TreeMap;
import java.util.concurrent.BrokenBarrierException;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.Semaphore;
import java.util.concurrent.SynchronousQueue;
import java.util.logging.Level;
import java.util.logging.Logger;

public class ServerGomokuGame implements Runnable {
    private ServerSocket serverConnection;
    private boolean keepProcessing = true;

    public ServerGomokuGame(final int port) {
        try {
            this.serverConnection = new ServerSocket(checkNotNull(port, "Port can't be null"));
        } catch (IOException ex) {
            Logger.getLogger(ServerGomokuGame.class.getName()).log(Level.SEVERE, null, ex);
        } 
    }

    @Override
    public void run(){ 
        try {
            while(keepProcessing){ 
                System.out.println("Waiting for new connections.");
                startNewGame(receiveConnectionsFromTwoPlayers());
            }
        } catch (IOException ex) {
            Logger.getLogger(ServerGomokuGame.class.getName()).log(Level.SEVERE, null, ex);
        } finally {
            try {
                serverConnection.close();
            } catch (IOException ex) {
                Logger.getLogger(ServerGomokuGame.class.getName()).log(Level.SEVERE, null, ex);
            }
        }
    }

        private void startNewGame(Socket[] twoPlayersConnections) throws IOException{
            new GomokuGame(twoPlayersConnections).runTheGame();
        }
        
            private Socket[] receiveConnectionsFromTwoPlayers() throws IOException{
                Socket[] connections = new Socket[2];
                for(int i = 0; i < 2; i++)
                    connections[i] = serverConnection.accept();
                System.out.println("Received connections from 2 players.");
                return connections;
            }

    public void stopRunning(){
        keepProcessing = false;
    }    
        

    private interface Communication{
    	public String receiveResponse(int gameId) throws IOException;
    	public void sendCommand(Command command, int gameId) throws IOException;
    	public void close() throws IOException;
    }
    
    private class TcpIpCommunication implements Communication{
        private final Socket connection;
        private final PrintWriter out;
        private final BufferedReader in;
        private JsonParser jsonParser = new JsonParser();
        private Gson gson = new Gson();
        
        
    	public TcpIpCommunication(Socket connection) throws IOException{
    		this.connection = connection;
            this.in = new BufferedReader(new InputStreamReader(connection.getInputStream()));
            this.out = new PrintWriter(connection.getOutputStream(), true);
    	}
    	
    	@Override
    	public String receiveResponse(int gameId) throws IOException{
    		String jsonResponse = in.readLine();
    		System.out.println(jsonResponse);
    		Gson gson = new Gson();
    		Command command = gson.fromJson(jsonResponse, Command.class);
    		
    		if(Protocol.INSTANCE.validateInput(command, gameId))
    			return command.getAdditionalValues().get(0);
    		else
    			throw new IOException();
    	}
    	
    	@Override
    	public void sendCommand(Command command, int gameId) throws IOException {
    		System.out.println(gson.toJson(command));
    		if(Protocol.INSTANCE.validateOutput(command, gameId))
    			out.println(gson.toJson(command));
    		
    	}    	
    	
    	public void close() throws IOException{
    		this.connection.close();
    		this.out.close();
    		this.in.close();
    	}
    }
    
    private class Command{
		
		private String command;
		private ArrayList<String> additionalValues = new ArrayList<String>();
		
		private Command(Protocol.Output command) {
			this.command = String.valueOf(command);
		}
		
		public void setCommand(Protocol.Output command) {
			this.command = String.valueOf(command);
		}
		
		public String getCommand() {
			return this.command;
		}
		
		public void addAdditionalValue(String value) {
			this.additionalValues.add(value);
		}
		
		public ArrayList<String> getAdditionalValues(){
			return this.additionalValues;
		}
	}

    private static enum Protocol {
        INSTANCE;
        public static enum Output{YOU_ARE_CONNECTED, 
                                  YOUR_BOARD_SYMBOL, 
                                  START_THE_GAME, 
                                  WAIT_FOR_YOUR_TURN, 
                                  NEW_MOVE, 
                                  INCORRECT_MOVE, 
                                  NEXT_PLAYER_TURN, 
                                  ANOTHER_PLAYER_COORDINATES, 
                                  YOU_WON, 
                                  YOU_LOST};
        public static enum Input{ MY_MOVE};
        private static enum State{START_STATE, 
                                  PLAYER_ONE_START, 
                                  READY_FOR_NEW_TURN, 
                                  WAITING_FOR_NEW_MOVE, 
                                  WINNER_INFORMED, 
                                  END_STATE};
        private SortedMap<Integer, State> gamesProtocolState = Collections.synchronizedSortedMap(new TreeMap<Integer, State>()); 
        
        public void createProtocolFiniteStateAutoma(final int gameId){
            gamesProtocolState.put(new Integer(gameId), State.START_STATE);
        }
                
        public boolean validateInput(Command command, final int gameId) throws IOException{
            if(processInput(command, gameId))
                return true;
            else
                throw new IOException("Exception while Protocol in " + gamesProtocolState.get(gameId) + " state");
        }

	        private boolean processInput(Command command, int gameId){
	        	if(Input.valueOf(command.getCommand()) == Input.MY_MOVE)
	        		if(gamesProtocolState.get(gameId) == State.WAITING_FOR_NEW_MOVE)
	        			return true;
	        		else return false;
        		else
        			return false;
	        }
        
        public boolean validateOutput(final Command command, final int gameId) throws IOException{
            if(processOutput(gameId, Output.valueOf(command.getCommand())))
        		return true;
            else
                throw new IOException("Exception while Protocol in " + gamesProtocolState.get(gameId) + " state");
        }
        
            private synchronized boolean processOutput(int gameId, final Output output){
                State currentState = gamesProtocolState.get(gameId);
                switch(currentState){
                    case START_STATE:
                        switch (output) {
                            case YOU_ARE_CONNECTED:
                                return true;
                            case START_THE_GAME:
                                gamesProtocolState.put(gameId, State.PLAYER_ONE_START);
                                return true;
                            case YOUR_BOARD_SYMBOL:
                                return true;
                            default:
                                return false;
                        }
                    case PLAYER_ONE_START:
                        if(output == Output.START_THE_GAME){
                            gamesProtocolState.put(gameId, State.READY_FOR_NEW_TURN);
                            return true;
                        }
                        else 
                            return false;
                    case READY_FOR_NEW_TURN:
                        switch (output) {
                            case WAIT_FOR_YOUR_TURN:
                                return true;
                            case NEW_MOVE:
                                gamesProtocolState.put(gameId, State.WAITING_FOR_NEW_MOVE);
                                return true;
                            case ANOTHER_PLAYER_COORDINATES:
                                gamesProtocolState.put(gameId, State.READY_FOR_NEW_TURN);
                                return true;
                            default:
                                return false;
                        }
                    case WAITING_FOR_NEW_MOVE:
                        switch (output) {
                            case INCORRECT_MOVE:
                                return true;
                            case NEXT_PLAYER_TURN:
                                gamesProtocolState.put(gameId, State.READY_FOR_NEW_TURN);
                                return true;
                            case YOU_WON:
                                gamesProtocolState.put(gameId, State.WINNER_INFORMED);
                                return true;
                            default:
                                return false;
                        }
                    case WINNER_INFORMED:
                        switch (output) {
                            case YOU_LOST:
                                gamesProtocolState.put(gameId, State.END_STATE);
                                return true;
                            case ANOTHER_PLAYER_COORDINATES:
                                return true;
                            default:
                                return false;
                        }
                    default: return false;
                }
            }


    }
    
    private class GomokuGame {
        private final int gameId;
        private final Player[] players = new Player[2];
        private final Semaphore onePlayerTurnAtATime = new Semaphore(1, true);
        private final SynchronousQueue<String> NewMoveCoordinates = new SynchronousQueue();
        private final CyclicBarrier waitForSecondPlayer = new CyclicBarrier(2);
        private final GameBoard gameBoard = new GameBoard();
        private volatile boolean isGameNotOver = true;
        private volatile boolean isItFirstTurn = true;
        
        GomokuGame(Socket[] playersConnections) throws IOException{
            if(playersConnections.length != 2)
                throw new IOException("Wrong connections amount");
            this.gameId = Arrays.hashCode(playersConnections);
            initPlayers(playersConnections);
            Protocol.INSTANCE.createProtocolFiniteStateAutoma(gameId);
        }

            private void initPlayers(Socket[] playersConnections) throws IOException{
                players[0] = new Player(playersConnections[0], 'X');
                players[1] = new Player(playersConnections[1], 'O');
            }
            
        public void runTheGame(){
            new Thread(players[0]).start(); 
            new Thread(players[1]).start(); 
            System.out.println("Game " + gameId + " starts.");
        }

        private class Player implements Runnable{
            private final char boardSymbol;
        	private final Communication communication;
        	
            Player(final Socket playerConnection, final char boardSymbol) throws IOException {
                this.communication = new TcpIpCommunication(playerConnection);
                this.boardSymbol = boardSymbol;
            }

            @Override
            public void run(){
                try {
                    sendInitMessagesToClient();
                    playTheGame();
                } catch (InterruptedException | IOException | BrokenBarrierException ex) {
                    Logger.getLogger(ServerGomokuGame.class.getName()).log(Level.SEVERE, null, ex);
                } finally {
                    try {
                        communication.close();
                    } catch (IOException ex) {
                        Logger.getLogger(ServerGomokuGame.class.getName()).log(Level.SEVERE, null, ex);
                    }
                }
            }    
                
                private void sendInitMessagesToClient() throws IOException, InterruptedException, BrokenBarrierException{
                    sendConnectionConfirm();
                    sendAssignedBoardSymbol();
                    sendStartCommand();
                    sendWaitCommand();
                }

                    private void sendConnectionConfirm() throws IOException, InterruptedException, BrokenBarrierException{
                    	this.communication.sendCommand(new Command(Protocol.Output.YOU_ARE_CONNECTED), gameId);
                        waitForSecondPlayer.await();
                    }

                    private void sendAssignedBoardSymbol() throws IOException, InterruptedException, BrokenBarrierException{
                        Command command = new Command(Protocol.Output.YOUR_BOARD_SYMBOL);
                    	if(this.boardSymbol == 'X') {
                    		command.addAdditionalValue("O");
                        	this.communication.sendCommand(command, gameId);
                    	}
                        else {
                        	command.addAdditionalValue("X");
                        	this.communication.sendCommand(command, gameId);
                        }
                        	waitForSecondPlayer.await();
                    }

                    private void sendStartCommand() throws IOException, InterruptedException, BrokenBarrierException{
                    	this.communication.sendCommand(new Command(Protocol.Output.START_THE_GAME), gameId);
                        waitForSecondPlayer.await();
                    }

                    private void sendWaitCommand() throws InterruptedException, BrokenBarrierException, IOException{
                    	this.communication.sendCommand(new Command(Protocol.Output.WAIT_FOR_YOUR_TURN), gameId);
                        waitForSecondPlayer.await();
                    }

                private void playTheGame() throws IOException, InterruptedException, BrokenBarrierException{
                    String newMoveCoordinates;
                    while(isGameNotOver){
                        onePlayerTurnAtATime.acquire();
                        sendOpponentsMoveCoordinates();
                        if(!isGameNotOver){
                            sendLostCommand();
                            break;
                        }
                        newMoveCoordinates = makeNewMove();
                        if(gameBoard.checkVictory(newMoveCoordinates)){
                        	endGame();
                        	isGameNotOver = false;
                        }
                        else
                        	continueGame();
                        	
                        onePlayerTurnAtATime.release();
                        NewMoveCoordinates.put(newMoveCoordinates);
                    }
                }

                    private void sendOpponentsMoveCoordinates() throws IOException, InterruptedException{
                        if(!isItFirstTurn){
                        	Command command = new Command(Protocol.Output.ANOTHER_PLAYER_COORDINATES);
                        	command.addAdditionalValue((String)NewMoveCoordinates.take());
                        	this.communication.sendCommand(command, gameId);
                        }
                        else
                            isItFirstTurn = false;
                    }
                
                    private void sendLostCommand() throws IOException{
                    	this.communication.sendCommand(new Command(Protocol.Output.YOU_LOST), gameId);
                    }
                    
                    private String makeNewMove() throws IOException, InterruptedException{
                    	sendNewMoveCommand();
                    	String newMoveCoordinates = receiveNewMove();
                        if(!gameBoard.isNewMoveCorrect(newMoveCoordinates))
                            newMoveCoordinates = makeCorrectMove();
                        gameBoard.addNewMove(newMoveCoordinates, boardSymbol);
                        return newMoveCoordinates;
                    }
                    
	                    private void sendNewMoveCommand() throws IOException{
	                    	this.communication.sendCommand(new Command(Protocol.Output.NEW_MOVE), gameId);
	                    }
                    	
	                    private String receiveNewMove() throws IOException{
	                    	return this.communication.receiveResponse(gameId);
	                    }
	                    
                    	private String makeCorrectMove() throws IOException{
                            String correctCoordinates;
                            do {
                            	sendIncorrectMoveCommand();
                            	correctCoordinates = receiveNewMove();
                            } while(!gameBoard.isNewMoveCorrect(correctCoordinates));

                            return correctCoordinates;
                        }
                    
                    	private void sendIncorrectMoveCommand() throws IOException{
                        	this.communication.sendCommand(new Command(Protocol.Output.INCORRECT_MOVE), gameId);
                    	}
             
                    private void endGame() throws IOException{
                    	this.communication.sendCommand(new Command(Protocol.Output.YOU_WON), gameId);
                        System.out.println("Game " + gameId + " ends.");
                    }
                    
                    private void continueGame() throws IOException {
                    	this.communication.sendCommand(new Command(Protocol.Output.NEXT_PLAYER_TURN), gameId);
                    	this.communication.sendCommand(new Command(Protocol.Output.WAIT_FOR_YOUR_TURN), gameId);
                    }
        } 

        private class GameBoard {
            private final HashBasedTable<Integer, Integer, Character> board;
            
            public GameBoard(){
                board = HashBasedTable.create(10,10);
            }

            public synchronized void addNewMove(String coordinates, char playerSymbol) throws IOException{
                board.put(getRow(coordinates), getCol(coordinates), playerSymbol);
            }

            public boolean isNewMoveCorrect(String coordinates){
                return !board.contains(getRow(coordinates), getCol(coordinates));
            }

                private int getRow(String coordinates){
                    return Arrays.asList(new String[]{"A","B","C","D","E","F","G","H","I","J"}).indexOf(coordinates.substring(0,1));
                }
                
                private int getCol(String coordinates){
                    return Integer.parseInt(coordinates.substring(1,2));
                }

            public boolean checkVictory(String  coordinates){
                return Victory.INSTANCE.isVictory(board, coordinates);
            }    
        }
    }
    
    private static enum Victory {
        INSTANCE;
        private final int SYMBOLS_FOR_VICTORY = 3;
        private final int BOARD_SIZE = 10;
        
        public boolean isVictory(final HashBasedTable board, String coordinates){
            return isHorizontalVictory(board, coordinates) || isVerticalVictory(board, coordinates) || isDiagonalVictory(board, coordinates);
        }

            private boolean isHorizontalVictory(final HashBasedTable board, final String coordinates){
                int sameSymbolsAmount = 1; 
                int col = getCol(coordinates);
                final Map<Integer, Character> rowMap = board.row(getRow(coordinates));
                
                while(col+1 <= BOARD_SIZE && rowMap.get(col+1) != null){
                    if(Objects.equals(rowMap.get(getCol(coordinates)), rowMap.get(col+1))){
                        sameSymbolsAmount++;
                        col++;
                    }
                    else break;
                }
                
                col = getCol(coordinates);
                while(col-1 >= 0 && rowMap.get(col-1) != null){
                    if(Objects.equals(rowMap.get(getCol(coordinates)), rowMap.get(col-1))){
                        sameSymbolsAmount++;
                        col--;
                    }
                    else break;
                }
                return sameSymbolsAmount >= SYMBOLS_FOR_VICTORY;
            }

            private boolean isVerticalVictory(final HashBasedTable board, final String coordinates){
                int sameSymbolsAmount = 1;
                int row = getRow(coordinates);
                final Map<Integer, Character> colMap = board.column(getCol(coordinates));
                
                while(row+1 <= BOARD_SIZE && colMap.get(row+1) != null){
                    if(Objects.equals(colMap.get(getRow(coordinates)), colMap.get(row+1))){
                        sameSymbolsAmount++;
                        row++;
                    }
                    else break;
                }
                
                row = getRow(coordinates);
                while(row-1 >= 0 && colMap.get(row-1) != null){
                    if(Objects.equals(colMap.get(getCol(coordinates)), colMap.get(row-1))){
                        sameSymbolsAmount++;
                        row--;
                    }
                    else break;
                }
                return sameSymbolsAmount >= SYMBOLS_FOR_VICTORY;
            }

            private boolean isDiagonalVictory(final HashBasedTable board, final String coordinates){
                int sameSymbolsAmount = 1 + getAmountOfSameDiagonalSymbolsInSouthEastDirection(board, coordinates) 
                                          + getAmountOfSameDiagonalSymbolsInNorthWestDirection(board, coordinates);
                return sameSymbolsAmount >= SYMBOLS_FOR_VICTORY;
            }
             
                private int getAmountOfSameDiagonalSymbolsInSouthEastDirection(final HashBasedTable board, final String coordinates){
                    int sameSymbolsAmount = 0;
                    int row = getRow(coordinates);
                    int col = getCol(coordinates);
                    while(row+1 <= BOARD_SIZE && col+1 <= BOARD_SIZE && board.get(row+1, col+1) != null){
                        if(board.get(getRow(coordinates), getCol(coordinates)) == board.get(row+1, col+1)){
                            sameSymbolsAmount++;
                            row++;
                            col++;
                        }
                        else return sameSymbolsAmount;
                    }
                    return sameSymbolsAmount;
                }
            
                private int getAmountOfSameDiagonalSymbolsInNorthWestDirection(final HashBasedTable board, final String coordinates){
                    int sameSymbolsAmount = 0;
                    int row = getRow(coordinates);
                    int col = getCol(coordinates);
                    while(row-1 >= 0 && col-1 >= 0 && board.get(row-1, col-1) != null){
                        if(board.get(getRow(coordinates), getCol(coordinates)) == board.get(row-1, col-1)){
                            sameSymbolsAmount++;
                            row--;
                            col--;
                        }
                        else break;
                    }
                    return sameSymbolsAmount;
                }
                
                private int getRow(String coordinates){
                    return Arrays.asList(new String[]{"A","B","C","D","E","F","G","H","I","J"}).indexOf(coordinates.substring(0,1));
                }

                private int getCol(String coordinates){
                    return Integer.parseInt(coordinates.substring(1,2));
                }
    }
}