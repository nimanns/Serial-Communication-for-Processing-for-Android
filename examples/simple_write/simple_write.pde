import com.processing.android.serial.*;

Serial my_port;

void setup() {
  fullScreen();
  //orientation(LANDSCAPE);  // change the orientation
  noStroke();
  fill(0);
  my_port = new Serial(this, 9600);
}

void draw() {
  background(220);
  if (mousePressed) {
    if (mouseX < width/2) {
      rect(0, 0, width/2, height); // Left
      //send a string to mcu
      my_port.write("left\n");
      // my_port.write("left");
    } else {
      rect(width/2, 0, width/2, height); // Right
      //send a string to mcu
      my_port.write("right\n");
    }
  }
}


/*
Arduino Code:

#define LED_1_PIN D9
#define LED_2_PIN D8

bool led_1_state = false;
bool led_2_state = false;

void setup() {
  // put your setup code here, to run once:
  Serial.begin(9600);
  pinMode(LED_1_PIN, OUTPUT);
  pinMode(LED_2_PIN, OUTPUT);
}

void loop() {
  // put your main code here, to run repeatedly:
  if (Serial.available()) {
    String input = Serial.readStringUntil('\n');
    if (input == "left") {
      led_1_state = !led_1_state;
    } else if (input == "right") {
      led_2_state = !led_2_state;
    }
  }

  if (led_1_state) {
    digitalWrite(LED_1_PIN, LOW);
  } else {
    digitalWrite(LED_1_PIN, HIGH);
  }

  if (led_2_state) {
    digitalWrite(LED_2_PIN, LOW);
  } else {
    digitalWrite(LED_2_PIN, HIGH);
  }
}

*/
