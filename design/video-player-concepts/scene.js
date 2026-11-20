// Original canvas artwork, rendered into local demo video. No external media.
function paintScene(canvas,t){
 const c=canvas.getContext('2d'),w=canvas.width,h=canvas.height; c.save();c.scale(w/720,h/1280);
 const gradient=(a,b,y0=0,y1=1280)=>{const g=c.createLinearGradient(0,y0,0,y1);g.addColorStop(0,a);g.addColorStop(1,b);return g};
 c.fillStyle=gradient('#708d92','#d9dfbd',0,900);c.fillRect(0,0,720,1280);
 const sun=c.createRadialGradient(510,315,12,510,315,270);sun.addColorStop(0,'#faf1cd99');sun.addColorStop(.23,'#faf1cd20');sun.addColorStop(1,'#faf1cd00');c.fillStyle=sun;c.fillRect(0,0,720,720);c.fillStyle='#edf0cd';c.beginPath();c.arc(510,315,35,0,Math.PI*2);c.fill();
 function shape(color,pts){c.fillStyle=color;c.beginPath();pts.forEach((p,i)=>i?c.lineTo(...p):c.moveTo(...p));c.closePath();c.fill()}
 // Slow atmospheric drift is video content, not UI decoration.
 c.save();c.translate(Math.sin(t*.2)*9,0);shape('#9caeaa',[[0,610],[0,485],[95,373],[174,458],[262,298],[354,432],[446,370],[548,489],[654,348],[750,493],[750,710]]);c.restore();
 shape(gradient('#688789','#acbca9',340,800),[[0,810],[0,582],[100,487],[173,534],[307,345],[440,558],[537,457],[720,610],[720,810]]);
 shape('#e0e5ce',[[307,345],[252,424],[280,411],[306,434],[319,408],[356,423]]);
 shape('#b2c7ba',[[307,345],[307,434],[319,408],[356,423]]);
 shape(gradient('#476a65','#829e85',500,900),[[0,936],[0,648],[56,588],[172,701],[254,649],[385,774],[497,670],[598,710],[720,580],[720,936]]);
 shape('#34584d',[[0,880],[0,721],[91,689],[182,768],[253,753],[386,842],[509,793],[603,815],[720,705],[720,950]]);
 c.fillStyle=gradient('#8da796','#294d43',835,1280);c.fillRect(0,848,720,432);
 // Distorted mountain reflections.
 c.save();c.globalAlpha=.21;shape('#d4dfba',[[310,853],[290,919],[335,956],[281,1033],[382,1106],[342,1165],[480,1280],[610,1280],[475,1136],[469,1026],[363,966],[371,912]]);c.restore();
 for(let i=0;i<78;i++){const y=862+i*5.4;const x=(i*167%740)-40+Math.sin(t*.8+i*.6)*(2+i*.09);c.strokeStyle=i%3===0?'#d8e5c541':'#b6ccba20';c.lineWidth=i%4===0?1.4:.7;c.beginPath();c.moveTo(x,y);c.lineTo(x+20+(i*23%90),y);c.stroke()}
 shape(gradient('#2c4d3c','#112e26',900,1280),[[0,957],[56,946],[112,970],[180,960],[240,992],[296,1034],[270,1081],[159,1132],[87,1212],[0,1255]]);
 shape(gradient('#355a42','#172e26',830,1280),[[720,870],[677,910],[645,993],[592,1052],[548,1088],[569,1139],[650,1170],[720,1187]]);
 function tree(x,y,size,color){shape(color,[[x,y-size],[x-size*.24,y-size*.4],[x-size*.1,y-size*.4],[x-size*.33,y],[x+size*.33,y],[x+size*.1,y-size*.4],[x+size*.24,y-size*.4]]);c.fillStyle=color;c.fillRect(x-2,y-7,4,23)}
 for(let i=0;i<21;i++)tree((i*43)%222-30,994+(i%4)*15,65+i%5*16,'#254a37');for(let i=0;i<9;i++)tree(635+(i*23)%100,990-i%3*30,82+i%4*28,'#254733');
 shape('#142f25',[[0,1233],[105,1199],[201,1216],[312,1266],[350,1280],[0,1280]]);shape('#1c3629',[[528,1280],[619,1209],[720,1224],[720,1280]]);
 // Foreground grass, deliberately fixed in space; only breeze moves tips.
 c.strokeStyle='#7f9b6b7a';c.lineWidth=1.2;for(let i=0;i<50;i++){const x=(i*31)%270,y=1250+i%4*10;c.beginPath();c.moveTo(x,y+20);c.quadraticCurveTo(x-5,y-4,x+Math.sin(t*1.3+i)*6,y-15-i%3*8);c.stroke()}
 c.restore();
}
