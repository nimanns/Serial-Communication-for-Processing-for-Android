import com.processing.android.serial.*;

Serial my_connection;
String data;

void setup() {
  my_connection = new Serial(this, 9600);
}

void draw() {
  background(0);
  
  if (my_connection.isConnectionEstablished()) {
    data = my_connection.readSerial(); // Read data from serial
  }
  textSize(50);
  text("Data: " + data, 10, 10, width, height);
}

/*
Arduino Code:

int counter = 0;

void setup() {
  // put your setup code here, to run once:
  Serial.begin(9600);
}

void loop() {
  // put your main code here, to run repeatedly:
  Serial.println(counter);
  counter++;
  if(counter==255){
    counter = 0;
  }
  delay(500);
}

*/
