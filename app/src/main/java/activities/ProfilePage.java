package activities;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.constraintlayout.widget.ConstraintLayout;
import androidx.fragment.app.Fragment;
import android.content.Intent;
import android.graphics.Color;
import android.os.Bundle;
import android.os.Handler;
import android.view.View;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;
import com.example.capstoneproject_1.R;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.Query;
import com.google.firebase.database.ValueEventListener;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.TimeZone;
import java.util.concurrent.TimeUnit;

import fragments.EditProfileFragment;
import models.ArduinoConnection;

public class ProfilePage extends AppCompatActivity {

    /** => Guncel yapida RegisterPage'de kullanicidan alinip Firebase uzerinde RealTimeDb'ye kaydedilen name, surname, email gibi bilgiler getirilip
           profil sayfasinda gosterilmekte ve EditProfileFragment'ta bu bilgiler degistirilince db'ye guncellenen verileri gonderip tekrar ProfilePage'e
           donulunce guncellenen veriler gosterilmekte.*/
    private  FirebaseAuth mAuth;
    private FirebaseUser currentUser;
    private String uId = "";
    private DatabaseReference reference; //FirebaseDatabase.getInstance().getReference().child("users").child();

    private Button btnOpenLock;
    private Button btnCancelReservation;
    private Button btnTerminateReservation; //=>New *==>

    private TextView txtCarParkName;
    private TextView txtDuration;

    private TextView txtName;
    private TextView txtSurname;
    private TextView txtEmail;
    private TextView txtPhoneNumber;

    private TextView lblReservationInfo; //=>new *=> (to change the label of this view if the reservation is in 'ARRIVED' status).

    /**=>'completeReservation()' metodunun cagrilmasi halinde 'isReservationCompleted' degiskeni true olarak atanir ve Handler gibi tekrar eden operasyonlarin sonlandirilmasinda
     * ve sayfanin yeniden/sifirdan cagrilmasinda (aktif rezervasyon sonlandirildigi veya iptal edildigi icin) kullanilir.*/
    private boolean isReservationCompleted = false;

    //=>Check whether the user is arrived for the reservation or not and control the buttons visibility accordingly;
    private boolean isUserArrived = false;

    /**=>Not:Login olan kullanicinin bilgileri Firebase db'den profil sayfasina yansitiliyor fakat verilerin ilgili viewlere setlenmesi birkac sn aliyor, bu daha hizli veya async yapilabilir mi
       diye bakilabilir. Su an icin duzenle ikonuna tiklaninca tetiklenen "moveToEditProfile()" metodunda email'in bos gelmesi, yani henuz db'den verilerin tam cekilememesi halinde kullaniciya
       bir uyari mesaji verilerek kontrol ediliyor ve veri cekilmezse duzenleme sayfasina gidilip yanlis bir veri guncellemesinin onune gecilmis oluyor. Email alani dolduktan sonra verilerin de
       cekildigi soylenebileceginden, bu durumda ikona tekrar tiklaninca 'EditProfileFragment' sayfasi aciliyor ve login olan kullaniciya ait veriler duzenlenip Firebase uzerinde guncellenip islem
       tamamlaninca ProfilePage'de tekrar gosteriliyor.*/

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_profile_page);

        txtCarParkName = findViewById(R.id.txtSelectedCarParkName);
        txtDuration = findViewById(R.id.txtDuration);
        btnOpenLock = findViewById(R.id.btnOpenLock);
        btnCancelReservation = findViewById(R.id.btnCancelReservation);
        btnTerminateReservation = findViewById(R.id.btnCompleteReservation);
        btnOpenLock.setVisibility(View.INVISIBLE);
        btnCancelReservation.setVisibility(View.INVISIBLE);
        btnTerminateReservation.setVisibility(View.INVISIBLE); //=>New *==>
        lblReservationInfo = findViewById(R.id.lblActiveReservationInfo); //=>New *==>

        txtName = findViewById(R.id.txtProfileName);
        txtSurname = findViewById(R.id.txtProfileSurname);
        txtEmail = findViewById(R.id.txtProfileEmail);
        txtPhoneNumber = findViewById(R.id.txtProfilePhoneNumber);

        //To fetch the relevant informations of currently logged in user;
        mAuth = FirebaseAuth.getInstance();
        currentUser = mAuth.getCurrentUser();

        if (currentUser != null) {
            uId = currentUser.getUid();
            reference = FirebaseDatabase.getInstance().getReference().child("users").child(uId);
            reference.addValueEventListener(new ValueEventListener() {
                @Override
                public void onDataChange(@NonNull DataSnapshot snapshot) {
                    //Login olan kullanicinin id'si ile RealTimeDb'ye bir tane veri kaydedileceginden for ile donmeden yalnzica bir veriyi alacak sekidle kontrol saglanailir;
                    txtName.setText(snapshot.child("name").getValue().toString());
                    txtSurname.setText(snapshot.child("surname").getValue().toString());
                    txtEmail.setText(snapshot.child("email").getValue().toString());
                    txtPhoneNumber.setText(snapshot.child("phoneNumber").getValue().toString());
                    System.out.println(txtName.getText() +"-" + txtSurname.getText() +"-"+txtEmail.getText()+"-"+txtPhoneNumber.getText());
                }
                @Override
                public void onCancelled(@NonNull DatabaseError error) {

                }
            });
        } else {
            Toast.makeText(ProfilePage.this, "No user is currently logged in!", Toast.LENGTH_SHORT).show();
        }

        //*=>If the user clicks on 'Open' or 'Cancel Reservation' buttons, then the active reservation of the user will be cancelled and be updated as 'PASSIVE' on the database;
        btnOpenLock.setOnClickListener(new View.OnClickListener() {  //*****----3
            @Override
            public void onClick(View view) {
                //completeReservation();
                arrivedForReservation();
            }
        });

        btnCancelReservation.setOnClickListener(new View.OnClickListener() { //*****----4
            @Override
            public void onClick(View view) {
                completeReservation();
            }
        });

        //=>'Complete Reservation' butonuna tiklandiginda asagidaki metot tetiklenmektedir;
        btnTerminateReservation.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                completeReservation();
                //=>'showReservationOfUser()' metodu icerisindeki Handler, calismayi 'Open Lock'a basildiginda zaten durdurdugu icin ' completeReservation()' icerisindeki
                // 'isReservationCompleted=true' kontrolu, ARRIVED statuler uzerinden rezervasyon kapatilirken gecerli olmuyor (or ile if kosuluna eklenmis olsa da).
                // Bu sebeple burada ayrica sayfa yenileniyor ve PASSIVE statude olan rezervasyonlar oldugundan sayfa acildiginda onCreate icerisindeki default
                // degerler (buton gorunurlukleri de dahil olmak uzere) ekrana yansiyor.
                Handler handler = new Handler();
                handler.postDelayed(new Runnable() {
                    @Override
                    public void run() {
                        recreate();
                    }
                }, 1000); //Execution of 'recreate()' will be delayed by 1 second to wait the completion of 'completeReservation()'
            }
        });

        //*****----1
        try {
            checkDurationOfReservation();
        } catch (ParseException e) {
            e.printStackTrace();
        }

        //=>uId'nin atanmasi sonrasinda cagrilmasi gerekli cunku reference icin login olan kullanicinin id'si kullaniliyor;
        showReservationOfUser();//**
    }

    //*=>Active or ARRIVED reservation of the logined user will be shown on the ProfilePage. Otherwise, the components/views will be left as default;
    private void showReservationOfUser(){
       DatabaseReference refReservation = FirebaseDatabase.getInstance().getReference().child("reservations");
       Query queryRes = refReservation.orderByChild("userId").equalTo(uId);
       queryRes.addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(DataSnapshot dataSnapshot) {
                boolean hasActiveReservation = false;
                String reservationStatus = null;
                Long rsvDateDb = null;
                String reservedCarPark = null;
                for (DataSnapshot childSnapshot : dataSnapshot.getChildren()) {
                    // Get the value of the reservationStatus field for the current reservation
                    reservationStatus = childSnapshot.child("reservationStatus").getValue(String.class);

                    /**=>Check if the reservationStatus is "ACTIVE" or "ARRIVED" since both status are unique by the control on the ReservationPage
                        that does not allow the user to make a reservation if he/she has an active or arrived status reservation already;*/
                    //Check the ACTIVE reservation status existence;
                    if (reservationStatus != null && reservationStatus.equals("ACTIVE")) {
                        hasActiveReservation = true;
                        rsvDateDb = childSnapshot.child("reservationDate").getValue(Long.class);
                        reservedCarPark = childSnapshot.child("reservedCarPark").getValue(String.class);
                        break;
                    }
                    //Check the ARRIVED reservation status existence;
                    if(reservationStatus != null && reservationStatus.equals("ARRIVED")){
                        isUserArrived = true;
                        reservedCarPark = childSnapshot.child("reservedCarPark").getValue(String.class);
                        break;
                    }
                }

                //=>If Handler is stopped and 'showReservationOfUser()' is called on the onCreate() again, this control will be provided to
                //assign the reserved car park name on 'txtCarParkName' view and making the 'Complete Reservation' button visible;
                if(isUserArrived){
                    lblReservationInfo.setText("Arrived Reservation Information"); //changing the 'Active' res. info label of user as 'Arrived' on the layout page.
                    txtCarParkName.setText(reservedCarPark);
                    btnTerminateReservation.setVisibility(View.VISIBLE);
                }

                if (hasActiveReservation) {
                    // The user has an active reservation
                    txtCarParkName.setText(reservedCarPark);

                    //=>Reservation date and current time will be compared and their difference will be set as the value of Duration on the ProfilePage.
                    final Long rsvDate = rsvDateDb;
                    /**=>Handler is used to update (as an animation) the TextView's value as each one second passes according to the current time while the ProfilePage
                     * is visible;*/
                    /**=> The posted 'Runnable' checks the time difference every second and updates the TextView with the updated duration value. The postDelayed method is used to
                         post the Runnable to the Handler object with a delay of 1000 milliseconds (i.e., 1 second) between each update.*/
                    final Handler handler = new Handler();
                    handler.post(new Runnable() {
                        @Override
                        public void run() {
                            try {
                                if (rsvDate != null) {
                                /*=>'isReservationCompleted' true ise aktif rezervasyon tamamlanmis/iptal edilmis(statusu 'PASSIVE' olarak guncellenmis) demektir ve bu sebeple
                                    Handler'in calismasi durdurulup ProfilePage activity'si (sayfasi) yeniden cagrilabilir.*/

                                   /*=>Benzer bir mantikla 'Open Lock' butonuna basilip kullanici yarim saat icerisinde rezervasyonuna vardiysa ve 'ACTIVE' rez. statusunu
                                        'ARRIVED' olarak guncellediyse de Handler'in calismasi durdurulup sayfa yeniden baslatilir ve onCreate() yeniden cagrilir.*/
                                    if (isReservationCompleted || isUserArrived) {
                                        handler.removeCallbacksAndMessages(null);
                                        recreate(); /*Aktif rezervasyonun statusu PASSIVE olarak guncellendikten sonra artik Handler ile zaman artisini saglayacak guncellemenin onune
                                   gecmek ve sayfayi yeniden baslatmak adina sayfayi yeniden tetikleyen ve sirasiyla onDestroy() ve onCreate() metotlarini cagiran 'recreate()'
                                   metodu cagrilabilir ve return ile tamamen yeni sayfa acilip bu fonksiyondan cikilabilir.*/
                                        return;
                                    }
                                    // Convert the server timestamp value to a Date object
                                    Date reservationDate = new Date((Long) rsvDate);
                                    // Calculate the time difference between reservationDate and the current time
                                    long currentTime = System.currentTimeMillis();
                                    long timeDiffMillis = currentTime - reservationDate.getTime();
                                    // Format the time difference as a string and set it to the TextView
                                    txtDuration.setText(formatDuration(timeDiffMillis));
                                    checkDurationOfReservation(); //*****----2 - If the current duration reaches to 30 minutes for the active reservation, then it will be cancelled!
                                }
                                handler.postDelayed(this, 1000); // Update the TextView every second
                            }
                            catch (ParseException e) {
                                e.printStackTrace();
                            }
                        }
                    });
                    //=>Aktif rezervasyon olmadiginda INVISIBLE olan butonlar VISIBLE hale getirilir ve ekranda gorunur olacak sekilde guncellenir;
                    btnOpenLock.setVisibility(View.VISIBLE);
                    btnCancelReservation.setVisibility(View.VISIBLE);
                }
                else {
                    // The user does not have an active reservation
                    System.out.println("Logined user's active reservation information cannot be fetched!");
                }
            }
            @Override
            public void onCancelled(DatabaseError databaseError) {
                // Handle error
            }
        });
    }

    //=>Below method defines the formatDuration method to format the time difference as a String;
    private String formatDuration(long durationMillis) {
        long minutes = TimeUnit.MILLISECONDS.toMinutes(durationMillis);
        long seconds = TimeUnit.MILLISECONDS.toSeconds(durationMillis) % 60;
        return String.format(Locale.getDefault(), "%02d:%02d", minutes, seconds);
    }

    public void moveToEditProfile(View v){
        /**=>This means email field cannot be fetched yet because of the performance issues etc. but it must be set since it cannot be null on the Firebase db for the logined user's infos.
         In order to prevent direct pass to edit page fragment without completing the fetching operation on db, the user can be informed about that and they can wait for a little moment.*/
        if(txtEmail.getText().toString().isEmpty()){
            Toast.makeText(ProfilePage.this, "Please wait until the user informations fetched completely...", Toast.LENGTH_SHORT).show();
            return;
        }

        // Get the root view of your activity
        ConstraintLayout constraintView = findViewById(R.id.constraintProfile);
        for (int i = 0; i < constraintView.getChildCount(); i++) {
            View childView = constraintView.getChildAt(i);
            if (!(childView instanceof FrameLayout)) {
                // Set the child view's visibility to GONE or INVISIBLE
                childView.setVisibility(View.GONE);
            }
        }
        Fragment mFragment = EditProfileFragment.newInstance(txtName.getText().toString(),txtSurname.getText().toString(),txtEmail.getText().toString(),txtPhoneNumber.getText().toString());
        getSupportFragmentManager().beginTransaction().replace(R.id.container_edit, mFragment).commit();
    }

    //Sign Out Operation;
    public void moveBackToLoginPage(View v){
        mAuth.signOut(); //the current user will be sign out and redirected to Login Page.
        Intent i = new Intent(this, LoginPage.class);
        startActivity(i);
    }

    /**=>This method will be called for the click event of 'Open' button, 'Cancel Reservation' button and also after checking the value of Duration in the 'onCreate()' method.
      =>This method directly updates the status of logined user's active reservation to 'PASSIVE';*/
    /**=> Morevoer, since the active reservation of the user is completed/cancelled, then the car park's availability where the user made reservation, should be updated as 'true'.
       Because it is available from now on since there is one charge station for the context of this project (for now). */
    private void completeReservation(){
        DatabaseReference refReservation = FirebaseDatabase.getInstance().getReference().child("reservations");
        Query queryRes = refReservation.orderByChild("userId").equalTo(uId);
        queryRes.addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(DataSnapshot dataSnapshot) {
                String rsvStatus = null;
                for (DataSnapshot snapshot : dataSnapshot.getChildren()) {
                    rsvStatus = snapshot.child("reservationStatus").getValue(String.class);

                    /** => A condition for "ARRIVED" status is added to complete the arrived reservation. It will be trigerred when the user clicks on the
                     * 'Complete Reservation' button after it becomes visible when the user clicks on the 'Open Lock' button and updates the 'ACTIVE' reservation status
                     * as 'ARRIVED' on the Realtime database in order to open the lock system that holds the charging cable behind it to charge the electrical vehicle.*/
                    if (rsvStatus != null && (rsvStatus.equals("ACTIVE") || rsvStatus.equals("ARRIVED"))) { // Check if the reservation's status is "ACTIVE";
                        String id = snapshot.getKey();
                        DatabaseReference idRef = refReservation.child(id);
                        idRef.child("reservationStatus").setValue("PASSIVE", new DatabaseReference.CompletionListener() {
                            @Override
                            public void onComplete(@Nullable DatabaseError error, @NonNull DatabaseReference ref) {
                                if (error == null) {
                                    Toast.makeText(ProfilePage.this, "Reservation is completed/cancelled!", Toast.LENGTH_SHORT).show();
                                    updateCarParkAvailability(snapshot.child("reservedCarPark").getValue(String.class));
                                    //--------------------------------------------------------
                                    openLock(snapshot.child("reservedCarPark").getValue(String.class)); //reserved car park will be sent as a parameter to be checked.
                                    //--------------------------------------------------------
                                } else {
                                    Toast.makeText(ProfilePage.this, "Reservation cannot be completed/cancelled!", Toast.LENGTH_SHORT).show();
                                }
                            }
                        });
                        isReservationCompleted = true; //controlled to stop the Handler and recreate() the page.
                        break;
                    }
                }
            }
            @Override
            public void onCancelled(DatabaseError databaseError) {
                // Handle error
            }
        });
    }

    /*=>Duration of logined user's active reservation is checked and if it reach to 30 minutes, then it will be cancelled and it's status will be updated as 'PASSIVE'
        on the database. */
    //=>Arka planda 30 dakikayi gecmesi ve ProfilePage acilinca bu rezervasyonun dusmesinin gereklililigini de goz onunde bulundurarak 30 dakika veya daha uzun bir sure
    //gecmisse aktif rezervasyonun tamamlanmasi/iptali saglanacak;
    private void checkDurationOfReservation() throws ParseException {
        String duration = txtDuration.getText().toString();
        SimpleDateFormat format = new SimpleDateFormat("mm:ss");
        format.setTimeZone(TimeZone.getTimeZone("UTC"));
        Date durationTime = format.parse(duration); //converts duration to a Date object
        Date thirtyMin = format.parse("30:00"); //the TextView's value will be compared with this value (30 minutes).
        if (durationTime.compareTo(thirtyMin) >= 0) {
            // The value of the TextView is greater than or equal to "30:00".
            completeReservation(); //this method will update the status of active reservation as 'PASSIVE' to complete or cancel it.
        }
    }

    //=>It updates the availability of car park as true (available) where the user made reservation after the reservation is completed or cancelled;
    public static void updateCarParkAvailability(String carPark){
        DatabaseReference refLoc = FirebaseDatabase.getInstance().getReference().child("locations");
        Query queryLocations = refLoc.orderByChild("title").equalTo(carPark);
        queryLocations.addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(DataSnapshot dataSnapshot) {
                for (DataSnapshot snapshot : dataSnapshot.getChildren()) {
                    String id = snapshot.getKey();
                    DatabaseReference idRef = refLoc.child(id);
                    idRef.child("isAvailable").setValue(true); //the availability is updated here, completeListener is not added.
                }
            }
            @Override
            public void onCancelled(DatabaseError databaseError) {
                // Handle error
            }
        });
    }

    //*=>Below method will be triggered when the user clicks on the 'Open Lock' method to update the status of 'ACTIVE' reservation as 'ARRIVED';
    private void arrivedForReservation(){
        DatabaseReference refActiveReservation = FirebaseDatabase.getInstance().getReference().child("reservations");
        Query userRes = refActiveReservation.orderByChild("userId").equalTo(uId);
        userRes.addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                String resStatus = "";
                for (DataSnapshot childSnapshot : snapshot.getChildren()) {
                    resStatus = childSnapshot.child("reservationStatus").getValue(String.class);
                    if (resStatus != null && resStatus.equals("ACTIVE")) { //check for the active reservation of the logined user (unique).
                        String id = childSnapshot.getKey();
                        DatabaseReference idRef = refActiveReservation.child(id);
                        /**=>The status of the unique 'ACTIVE' reservation will be updated as 'ARRIVED' when the user clicks the 'OPEN' button
                         and that shows he/she arrives for the reservation in 30 minutes. This status will also be unique by controlling it with the 'ACTIVE'
                         status on the 'Reservation Page' since the user cannot make a reservation if he/she has an ACTIVE or ARRIVED reservation;*/
                        idRef.child("reservationStatus").setValue("ARRIVED", new DatabaseReference.CompletionListener() {
                            @Override
                            public void onComplete(@Nullable DatabaseError error, @NonNull DatabaseReference ref) {
                                if (error == null) {
                                    Toast.makeText(ProfilePage.this, "The user is arrived for the reservation.", Toast.LENGTH_SHORT).show();
                                    //--------------------------------------------------------
                                    openLock(childSnapshot.child("reservedCarPark").getValue(String.class)); //reserved car park will be sent as a parameter to be checked.
                                    //--------------------------------------------------------
                                } else {
                                    Toast.makeText(ProfilePage.this, "Arrive operation cannot be handled!", Toast.LENGTH_SHORT).show();
                                }
                            }
                        });
                        isUserArrived = true;
                        break;
                    }
                }
            }
            @Override
            public void onCancelled(@NonNull DatabaseError error) {
            }
        });
    }

    //=>Arduino Connection-Open The Lock System;
    /*=>The below method is called after the reservation is applied successfully in order to trigger the servo motor on the NodeMCU
       which will close the lock system that is connected to it;*/
    private void openLock(String resCarPark){
        //*The lock system will be triggered if the reserved car park is 'BAU South Campus';
        if(resCarPark.equals("BAU South Campus"))
           ArduinoConnection.sendCommand("/Lock=ON");
    }

}

/** Note: 1- The duration TextView's value will be checked only once via calling 'checkDurationOfRes()' in the 'onCreate()' method (the 'onCreate()' method of an Activity in
 *        Android is called only once when the Activity is first created).
 *  2- However, it also called in the 'showActiveReservation()' method which is called by each minute passes in order to update the duration TextView's value. Thus, the passing
 *  duration for the active reservation is always controlled by this method while the page is opening and it will be updated as 'PASSIVE' to be cancelled if it reaches to the 30
 *  minutes (as a constraint).
 * 3- If the user clicks on 'Open' button, then the active reservation of the logined user will be completed.
 * 4- If the user clicks on the 'Cancel Reservation' button, then the active reservation of the user will be cancelled.
 * ('checkDurationOfReservation()' metodunun cagrildigi yerler '*****----1', '*****----2' , '*****----3',  '*****----4' olarak isaretlendi.
 * */
//---------------------------------------------------------------------------------------------------------
/** NOTE-TR: Ozetle, aktif rezervasyon ustunden yarim saat gecmesi halinde, ProfilePage acildigi anda, sayfa her dakika guncellenirken gecen surenin yarim saate (30 dakika)
 * ulasmasi halinde veya 'Cancel Reservation' butonuna tiklanmasi halinde aktif rezervasyonun statusu db'de 'PASSIVE' olarak guncellenip rezervasyon iptal edilmektedir.
 * => Buna ek olarak, 'OPEN' butonuna tiklanmasi halinde ayni sekilde aktif rezervasyon statusu 'PASSIVE' olarak guncellenip rezervasyon tamamlanmaktadir. Tum bu islemler ayni
 *  * sekilde db'yi guncellediginden ortak olarak 'checkDurationOfReservation()' metodunu kullanmaktadirlar.
 **=> Ilk iki durum 'checkDurationOfReservation()' metodu (icerisinde 'completeReservation()' metodunu cagrir)  cagrilarak kontrol edilir, 'Cancel Reservation' ve 'Open' metotlarina
 * tiklanildiginda ise 'completeReservation()' metodu direkt cagrilir!
 * => 'completeReservation()' metodunun icerisinde ayrica 'updateCarParkAvailability()' metodu cagrilarak aktif rezervasyonun bulundugu car park'in statusu de rezervasyon
 *    tamamlandigi/iptal edildigi icin musait olarak ('isAvailable:true') guncellenir. *
 * */

/*=>DONE TO DO(S);
//=>'Open' butonuna tiklanildiginda kilit acilsa da 'isAvailable' kolonunun true olarak guncellenmemesi daha dogru olur. Kullanici aracini sarj ettiginde bu alanin musait
// olarak gozukmemesi gerektiginden buna ait bir kontrol eklenecek veya 'Open' butonu yalnizca kilidi acacak, isAvailability alanini guncellemeyecek. 'Complete Reservation' gibi
// farkli bir metot eklenip kullanici islemini bitirince bu butona tikladiginda rezervasyon tamamlanip 'isAvailable' alani true olarak guncellenip car park musait hale getirilebilir.
//=>Kullanici profil sayfasini actiginda yarim saat gecmesi halinde rezervasyonu iptal etme durumunu otomatik bir hale getirip job kontrolu gibi kullanicinin ProfilePage'i tekrar
//  manuel olarak acmasina gerek olmadan iptal edilebilmesi dusunulebilir.*/


/** NOT:: Saniyede bir Handler araciyla yeniden tetiklenen ve Realtime Db'de login olan kullanici icin ACTIVE res. olmasi halinde resDate donerek if bloguna dahil olup calisan yapi icin
 * (showReservationOfUser() metodu icerisinde) her saniye kontrol edilen run metodunda 'isReservationCompleted'a ek olarak or operatoru ile 'isUserArrived' kosulu da eklendi.
 * Kullanici 'Open Lock' butonuna basip 'isUserArrived' degiskenini true olarak guncellediginde bu Handler'in calismasi durdurulacak ve sayfa recreate() edilip onCreate() metodu
 * tekrar cagrildiginda txtDuration degeri 'X' olacagindan 'checkDurationOfReservation()' metodundaki yarim saat kontrolu txtDuration uzerinde yapilmayacak ve 'ARRIVED' statuye
 * guncellenen rezervasyon icin tamamlanan veya iptal olan (statusu 'PASSIVE' olarak guncellenen) rezervasyonlardan farkli olarak, rezervasyonun bagli bulundugu carPark'in
 * RealTime Db'deki 'locations' altinda bulunan isAvailable' degeri 'true' olarak guncellenmeyecek. Boylece, bir kullanici 'Open Lock' butonuna bassa da aracini sarj etme islemi
 * bitmeden rezervasyonu tamamlanmamis olacak.
 *    === Buna ek olarak, yine 'showReservationOfUser()' metodu icerisine login olan kullanici icin 'ARRIVED' statulu rezervasyon varsa isUserArrived=true olarak guncelleyip
 *    (for icerisindeki ikinci if kontrolu ile) car park name'inin alinmasi ve 'Complete Reservation' butonunun gorunur hale getirilmesi (for disindaki ilk if ile) belirtilerek
 *    'Open Lock' butonuna tiklandiktan sonra Handler'in saniyelik kontrolu ile (yukarida detayli olarak belirtildigi gibi) tekrar tetiklenen onCreate() icerisinde cagrilan
 *    showReservationOfUser() metodunda bu kontrol saglanmis olup statusu kilit acilinca 'ARRIVED' olarak guncellenen rezervasyona ait carParkName bilgisi ve ilgili buton
 *    gorunurlugu tek bir metot icerisindeki kontrol ile aktif edilip bu dogrultuda guncelleme saglanmis olur. 'hasActiveReservation' false olacagindan diger if'e tekrar dahil
 *    olunmaz.
 * */

