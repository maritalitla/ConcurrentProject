//Prototype implementation of Car Control
//Mandatory assignment
//Course 02158 Concurrent Programming, DTU, Fall 2016

//Hans Henrik Lovengreen    Oct 3, 2016

import java.awt.Color;
import java.util.ArrayList;
import java.util.List;

class Gate {

	Semaphore g = new Semaphore(0);
	Semaphore e = new Semaphore(1);
	boolean isopen = false;

	public void pass() throws InterruptedException {
		g.P();
		g.V();
	}

	public void open() {
		try {
			e.P();
		} catch (InterruptedException e) {
		}
		if (!isopen) {
			g.V();
			isopen = true;
		}
		e.V();
	}

	public void close() {
		try {
			e.P();
		} catch (InterruptedException e) {
		}
		if (isopen) {
			try {
				g.P();
			} catch (InterruptedException e) {
			}
			isopen = false;
		}
		e.V();
	}

}

class Car extends Thread {

	int basespeed = 100; // Rather: degree of slowness
	int variation = 50; // Percentage of base speed

	CarDisplayI cd; // GUI part

	int no; // Car number
	Pos startpos; // Startpositon (provided by GUI)
	Pos barpos; // Barrierpositon (provided by GUI)
	Color col; // Car color
	Gate mygate; // Gate at startposition

	int speed; // Current car speed
	Pos curpos; // Current position
	Pos newpos; // New position to go to

	Pos[][] positions; // Keeps track of positions of other cars.
	Semaphore mutexPositions; // Semaphore for mutual exclusion when driving.
	Alley alley; // Contains semaphores and other information for the alley.
	Barrier barrier;

	public Car(int no, CarDisplayI cd, Gate g, Pos[][] positions, Semaphore mutexDrive, Alley alley, Barrier barrier) {
		this.alley = alley;
		this.barrier = barrier;
		this.no = no;
		this.cd = cd;
		mygate = g;
		startpos = cd.getStartPos(no);
		barpos = cd.getBarrierPos(no); // For later use
		this.positions = positions;
	

		col = chooseColor(no);

		// do not change the special settings for car no. 0
		if (no == 0) {
			basespeed = 0;
			variation = 0;
			setPriority(Thread.MAX_PRIORITY);
		}
		positions[no][0] = startpos;
		positions[no][1] = startpos;
		this.mutexPositions = mutexDrive;
		

	}

	public synchronized void setSpeed(int speed) {
		if (no != 0 && speed >= 0) {
			basespeed = speed;
		} else
			cd.println("Illegal speed settings");
	}

	public synchronized void setVariation(int var) {
		if (no != 0 && 0 <= var && var <= 100) {
			variation = var;
		} else
			cd.println("Illegal variation settings");
	}

	synchronized int chooseSpeed() {
		double factor = (1.0D + (Math.random() - 0.5D) * 2 * variation / 100);
		return (int) Math.round(factor * basespeed);
	}

	private int speed() {
		// Slow down if requested
		final int slowfactor = 3;
		return speed * (cd.isSlow(curpos) ? slowfactor : 1);
	}

	Color chooseColor(int no) {
		return Color.blue; // You can get any color, as longs as it's blue
	}

	Pos nextPos(Pos pos) {
		// Get my track from display
		return cd.nextPos(no, pos);
	}

	boolean atGate(Pos pos) {
		return pos.equals(startpos);
	}

	public void run() {
		try {

			speed = chooseSpeed();
			curpos = startpos;
			cd.mark(curpos, col, no);

			while (true) {
				sleep(speed());

				if (atGate(curpos)) {
					mygate.pass();
					speed = chooseSpeed();
				}

				newpos = nextPos(curpos);

				// Start of critical section
				mutexPositions.P();
				// Check if there is a car at nextpos
				if (!this.checkNextPos()) {
					continue;
				}
				// End of critical section
				mutexPositions.V();
				// If the car is about to enter the critical section

				if (no < 5 && no != 0 && (curpos.row == 2 && curpos.col == 1 || curpos.row == 1 && curpos.col == 3)) {
					// CCW
					// Remove newpos from the occupied list
					mutexPositions.P();
					positions[no][1] = curpos;
					mutexPositions.V();

					alley.alleyMutexCCW.P();
					if (alley.ccwCounter == 0) {
						alley.mutexAlley.P();
					}
					alley.enter(no);
					alley.alleyMutexCCW.V();

					// insert newpos from the occupied list
					mutexPositions.P();
					positions[no][1] = newpos;
					mutexPositions.V();
				} else if (no > 4 && curpos.row == 10 && curpos.col == 0) {
					// CW
					// Remove newpos from the occupied list
					mutexPositions.P();
					positions[no][1] = curpos;
					mutexPositions.V();

					alley.alleyMutexCW.P();
					if (alley.cwCounter == 0) {
						alley.mutexAlley.P();
					}
					alley.enter(no);
					alley.alleyMutexCW.V();

					// insert newpos from the occupied list
					mutexPositions.P();
					positions[no][1] = newpos;
					mutexPositions.V();
				}
				System.out.println(this.barrier);
				if (curpos.col > 2 && (no <= 4 && curpos.row == 6 || no >= 5 && curpos.row == 5)) {
					barrier.sync();
				}
				// Move to new position
				cd.clear(curpos);
				cd.mark(curpos, newpos, col, no);
				sleep(speed());

				// Remove curpos from occupied list. This ensures that there is
				// no gap between the cars
				mutexPositions.P();
				positions[no][0] = newpos;
				mutexPositions.V();

				cd.clear(curpos, newpos);
				cd.mark(newpos, col, no);

				curpos = newpos;

				// If the car has left the critical section
				if (no < 5 && no != 0 && curpos.row == 9 && curpos.col == 1) {
					alley.alleyMutexCCW.P();
					alley.leave(no);
					if (alley.ccwCounter == 0) {
						alley.mutexAlley.V();
					}
					alley.alleyMutexCCW.V();
				} else if (no > 4 && curpos.row == 0 && curpos.col == 2) {
					alley.alleyMutexCW.P();
					alley.leave(no);
					if (alley.cwCounter == 0) {
						alley.mutexAlley.V();
					}
					alley.alleyMutexCW.V();
				}

			}

		} catch (Exception e) {
			cd.println("Exception in Car no. " + no);
			System.err.println("Exception in Car no. " + no + ":" + e);
			e.printStackTrace();
		}
	}

	private boolean checkNextPos() {
		boolean occupied = false;
		for (int i = 0; i < 9; i++) {
			if (positions[i][0].equals(newpos) || positions[i][1].equals(newpos)) {
				occupied = true;
				break;
			}
		}
		if (!occupied) {
			// Update positions array
			positions[no][0] = curpos;
			positions[no][1] = newpos;
			return true;
		} else {
			// If tile is occupied just continue in the while loop
			mutexPositions.V();
			return false;
		}
	}

}

class Alley {
	volatile Semaphore alleyMutexCW;
	volatile Semaphore alleyMutexCCW;
	volatile Semaphore mutexAlley;

	int cwCounter, ccwCounter = 0;

	public Alley() {
		alleyMutexCW = new Semaphore(1);
		alleyMutexCCW = new Semaphore(1);
		mutexAlley = new Semaphore(1);

	}

	public void enter(int no) {
		if (no < 5 && no != 0) {
			ccwCounter++;
		} else if (no > 4) {
			cwCounter++;
		}
	}

	public void leave(int no) {
		if (no < 5 && no != 0) {
			ccwCounter--;
		} else if (no > 4) {
			cwCounter--;
		}
	}

	@Override
	public String toString() {
		return "alleyMutexCW: " + this.alleyMutexCW + " alleyMutexCCW: " + this.alleyMutexCCW + " mutexAlley: "
				+ this.mutexAlley + " cwCounter: " + this.cwCounter + " ccwCounter: " + this.ccwCounter;
	}

}

class Barrier {
	Semaphore turnstile1 = new Semaphore(0);
	Semaphore turnstile2 = new Semaphore(1);
	Semaphore mutex = new Semaphore(1);
	boolean isBarrierOn = false;
	
	int carsWaiting = 0;
	int carAmount;
	
	

	public Barrier(int carAmount) {
		this.carAmount = carAmount;
	}

	@Override
	public String toString() {
		String s = "carsWaiting: " + carsWaiting + " turnstile1: " + turnstile1 + " turnstile2: " + turnstile2 + " mutex: "+ mutex;
		return s;
	}

	public void sync() throws InterruptedException { // Wait for others // active)
		if(isBarrierOn){
			mutex.P();
			carsWaiting++;
			if(carsWaiting == carAmount){
				turnstile2.P();
				turnstile1.V();
			}
			mutex.V();
			
			turnstile1.P();
			turnstile1.V();
			
			//barrier
			
			mutex.P();
			carsWaiting--;
			if(carsWaiting == 0){
				turnstile1.P();
				turnstile2.V();
			}
			mutex.V();
			
			turnstile2.P();
			turnstile2.V();
		}
	
	}

	public void on() { // Activate barrier
		isBarrierOn = true;
		
		
	}

	public void off() { // Deactivate barrier
		System.out.println("barrier off");
		isBarrierOn = false;
		try {
			mutex.P();
			turnstile2.P();//Close the second turnstile
			turnstile1.V();//Open the first turnstile to let the first car pass through
			//The first car will then let the other cars through
		} catch (InterruptedException e) {
			//TODO 
		}
		mutex.V();
	}

}


public class CarControl implements CarControlI {

	CarDisplayI cd; // Reference to GUI
	Car[] car; // Cars
	Gate[] gate; // Gates
	static volatile Alley alley;
	static volatile Barrier barrier;
	static volatile Semaphore mutexDrive;
	static volatile Pos[][] positions;

	public CarControl(CarDisplayI cd) {
		this.cd = cd;

		car = new Car[9];
		gate = new Gate[9];
		alley = new Alley();
		barrier = new Barrier(9);
		//Coordinator c = new Coordinator(9, barrier);
		//c.start();

		positions = new Pos[9][2];
		mutexDrive = new Semaphore(1);

		for (int no = 0; no < 9; no++) {
			gate[no] = new Gate();
			car[no] = new Car(no, cd, gate[no], positions, mutexDrive, alley, barrier);
			car[no].start();
		}
	}

	public void startCar(int no) {
		gate[no].open();
	}

	public void stopCar(int no) {
		gate[no].close();
	}

	public void barrierOn() {
		barrier.on();
	}

	public void barrierOff() {
		barrier.off();
	}

	public void barrierSet(int k) {
		cd.println("Barrier threshold setting not implemented in this version");
		// This sleep is for illustrating how blocking affects the GUI
		// Remove when feature is properly implemented.
		try {
			Thread.sleep(3000);
		} catch (InterruptedException e) {
		}
	}

	public void removeCar(int no) {
		cd.println("Remove Car not implemented in this version");
	}

	public void restoreCar(int no) {
		cd.println("Restore Car not implemented in this version");
	}

	/* Speed settings for testing purposes */

	public void setSpeed(int no, int speed) {
		car[no].setSpeed(speed);
	}

	public void setVariation(int no, int var) {
		car[no].setVariation(var);
	}

}