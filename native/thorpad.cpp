// Thorhaven input bridge. Original MIT code; Linux UAPI only.
#include <linux/input.h>
#include <linux/uinput.h>
#include <sys/socket.h>
#include <sys/un.h>
#include <sys/ioctl.h>
#include <poll.h>
#include <fcntl.h>
#include <unistd.h>
#include <signal.h>
#include <chrono>
#include <cmath>
#include <cstring>
#include <iostream>
#include <sstream>
#include <vector>
#include <array>
#include <algorithm>
static volatile sig_atomic_t alive=1;
static void signalStop(int){alive=0;}
static bool bit(const unsigned long *b,int n){return b[n/(8*sizeof(long))]&(1UL<<(n%(8*sizeof(long))));}
static const int buttons[]={304,305,307,308,310,311,312,313,314,315,316,317,318,544,545,546,547};
static bool targetOK(int n){if(n==0)return true; for(int b:buttons)if(n==b)return true; return n==1||n==28||n==57||(n>=103&&n<=108)||n==17||n==30||n==31||n==32;}
static int transform(int value,const input_absinfo &from,const input_absinfo &to,int dead,bool invert,int curve){
 double half=(from.maximum-from.minimum)/2.0;if(half<=0)return value;
 double x=std::clamp((value-(from.minimum+half))/half,-1.0,1.0);if(invert)x=-x;
 double dz=dead/100.0;double a=std::abs(x);a=a<=dz?0:(a-dz)/(1-dz);
 if(curve==1)a=a*a;else if(curve==2)a=std::sqrt(a);
 double h=(to.maximum-to.minimum)/2.0;return std::lround(to.minimum+h+std::copysign(a,x)*h);
}
static std::string escaped(const char *s){std::string r;for(;*s;s++){unsigned char c=*s;if(c=='"'||c=='\\')r+='\\';if(c>=32)r+=c;}return r;}
static bool candidate(int fd){unsigned long keys[(KEY_MAX+8*sizeof(long))/(8*sizeof(long))]={},abs[(ABS_MAX+8*sizeof(long))/(8*sizeof(long))]={};char name[256]={};ioctl(fd,EVIOCGNAME(sizeof(name)),name);ioctl(fd,EVIOCGBIT(EV_KEY,sizeof(keys)),keys);ioctl(fd,EVIOCGBIT(EV_ABS,sizeof(abs)),abs);return strncmp(name,"Thorhaven Controller",20)&&bit(keys,BTN_GAMEPAD)&&bit(abs,ABS_X)&&bit(abs,ABS_Y);}
struct Pad {
 int source=-1,out=-1;std::array<int,KEY_MAX+1> map{},held{},counts{};std::array<input_absinfo,ABS_MAX+1> ranges{};std::array<bool,ABS_MAX+1> axes{};
 int left=10,right=10,mask=0,swap=0,curve=0,rx=ABS_RX,ry=ABS_RY;long long emergency=0;
 Pad(){for(int i=0;i<=KEY_MAX;i++)map[i]=i;}
 static long long now(){return std::chrono::duration_cast<std::chrono::milliseconds>(std::chrono::steady_clock::now().time_since_epoch()).count();}
 bool emit(int type,int code,int value){input_event e{};e.type=type;e.code=code;e.value=value;return write(out,&e,sizeof(e))==sizeof(e);}
 void release(){if(out>=0){for(int i=0;i<=KEY_MAX;i++)if(counts[i])emit(EV_KEY,i,0);for(int i=0;i<=ABS_MAX;i++)if(axes[i])emit(EV_ABS,i,i==ABS_HAT0X||i==ABS_HAT0Y?0:(i==ABS_X||i==ABS_Y||i==rx||i==ry?(ranges[i].minimum+ranges[i].maximum)/2:ranges[i].minimum));emit(EV_SYN,SYN_REPORT,0);}held.fill(0);counts.fill(0);}
 void stop(){release();if(source>=0){ioctl(source,EVIOCGRAB,0);close(source);source=-1;}if(out>=0){ioctl(out,UI_DEV_DESTROY);close(out);out=-1;}emergency=0;}
 ~Pad(){stop();}
 std::string start(int index){if(index<0||index>255)return "ERR device";stop();std::string path="/dev/input/event"+std::to_string(index);source=open(path.c_str(),O_RDONLY|O_NONBLOCK|O_CLOEXEC);if(source<0||!candidate(source)){stop();return "ERR Geen bruikbare gamepad of geen roottoegang";}
 out=open("/dev/uinput",O_WRONLY|O_NONBLOCK|O_CLOEXEC);if(out<0){stop();return "ERR uinput vereist roottoegang";}
 unsigned long keys[(KEY_MAX+8*sizeof(long))/(8*sizeof(long))]={},abs[(ABS_MAX+8*sizeof(long))/(8*sizeof(long))]={};ioctl(source,EVIOCGBIT(EV_KEY,sizeof(keys)),keys);ioctl(source,EVIOCGBIT(EV_ABS,sizeof(abs)),abs);
 if(!bit(keys,BTN_SELECT)||!bit(keys,BTN_START)){stop();return "ERR Deze controller mist Select of Start voor de fysieke noodstop";}
 bool ok=ioctl(out,UI_SET_EVBIT,EV_KEY)==0&&ioctl(out,UI_SET_EVBIT,EV_ABS)==0;
 for(int k=0;k<=KEY_MAX;k++)if(bit(keys,k)||targetOK(k))ok&=ioctl(out,UI_SET_KEYBIT,k)==0;
 for(int a=0;a<=ABS_MAX;a++){axes[a]=bit(abs,a);if(axes[a]){ok&=ioctl(source,EVIOCGABS(a),&ranges[a])==0;ok&=ioctl(out,UI_SET_ABSBIT,a)==0;uinput_abs_setup setup{};setup.code=a;setup.absinfo=ranges[a];ok&=ioctl(out,UI_ABS_SETUP,&setup)==0;}}
 if(!axes[ABS_RX]&&axes[ABS_Z]){rx=ABS_Z;ry=ABS_RZ;}else{rx=ABS_RX;ry=ABS_RY;}
 uinput_setup setup{};strcpy(setup.name,"Thorhaven Controller");ioctl(source,EVIOCGID,&setup.id); // Preserve the device identity for Android controller layouts.
 ok&=ioctl(out,UI_DEV_SETUP,&setup)==0;ok&=ioctl(out,UI_DEV_CREATE)==0;
 if(!ok||ioctl(source,EVIOCGRAB,1)!=0){stop();return "ERR Virtuele controller of exclusieve toegang mislukt";}
 return "OK Controller actief";
 }
 std::string config(std::istringstream &s){int l,r,m,sw,c;std::array<int,17> targets{};if(!(s>>l>>r>>m>>sw>>c)||l<0||l>40||r<0||r>40||m<0||m>15||sw<0||sw>1||c<0||c>2)return "ERR config";for(int &t:targets)if(!(s>>t)||!targetOK(t))return "ERR button";std::string excess;if(s>>excess)return "ERR extra";if(sw&&source>=0&&(!axes[rx]||!axes[ry]))return "ERR Geen rechterstick om te wisselen";release();left=l;right=r;mask=m;swap=sw;curve=c;for(int i=0;i<17;i++)map[buttons[i]]=targets[i];return "OK Profiel toegepast";}
 bool key(int code,int value){if(value==2)return true;int old=held[code],v=value?1:0;held[code]=v;int t=map[code];if(t&&old!=v){int before=counts[t];counts[t]+=v?1:-1;if((before==0)!=(counts[t]==0)&&!emit(EV_KEY,t,counts[t]>0))return false;}return true;}
 bool events(){input_event e;while(read(source,&e,sizeof(e))==sizeof(e)){
 if(e.type==EV_SYN&&e.code==SYN_DROPPED){stop();return false;}
 if(e.type==EV_KEY&&e.code<=KEY_MAX){if(!key(e.code,e.value))return false;
 if(held[BTN_SELECT]&&held[BTN_START]){if(!emergency)emergency=now();}else emergency=0;
 }else if(e.type==EV_ABS&&(e.code==ABS_HAT0X||e.code==ABS_HAT0Y)){int negative=e.code==ABS_HAT0X?BTN_DPAD_LEFT:BTN_DPAD_UP,positive=e.code==ABS_HAT0X?BTN_DPAD_RIGHT:BTN_DPAD_DOWN;if(!key(negative,e.value<0)||!key(positive,e.value>0))return false;
 }else if(e.type==EV_ABS&&e.code<=ABS_MAX){int a=e.code,d=a,inv=0,dz=0;bool stick=true;if(a==ABS_X){dz=left;inv=mask&1;if(swap)d=rx;}else if(a==ABS_Y){dz=left;inv=mask&2;if(swap)d=ry;}else if(a==rx){dz=right;inv=mask&4;if(swap)d=ABS_X;}else if(a==ry){dz=right;inv=mask&8;if(swap)d=ABS_Y;}else stick=false;
 if(!emit(EV_ABS,d,stick?transform(e.value,ranges[a],ranges[d],dz,inv,curve):e.value))return false;
 }else if(e.type==EV_SYN&&e.code==SYN_REPORT&&!emit(EV_SYN,SYN_REPORT,0))return false;
 }return true;}
};
static void probe(){std::cout<<"{\"uid\":"<<getuid()<<",\"uinput\":"<<(access("/dev/uinput",W_OK)==0?"true":"false")<<",\"devices\":[";bool first=true;for(int i=0;i<256;i++){std::string p="/dev/input/event"+std::to_string(i);int fd=open(p.c_str(),O_RDONLY|O_NONBLOCK|O_CLOEXEC);if(fd<0)continue;if(candidate(fd)){char name[256]={};input_id id{};ioctl(fd,EVIOCGNAME(sizeof(name)),name);ioctl(fd,EVIOCGID,&id);if(!first)std::cout<<",";first=false;std::cout<<"{\"event\":"<<i<<",\"name\":\""<<escaped(name)<<"\",\"bus\":"<<id.bustype<<"}";}close(fd);}std::cout<<"]}\n";}
static int serve(int uid,const std::string &nonce){if(nonce.size()!=32||nonce.find_first_not_of("0123456789abcdef")!=std::string::npos)return 2;
 int server=socket(AF_UNIX,SOCK_STREAM|SOCK_CLOEXEC,0);sockaddr_un addr{};addr.sun_family=AF_UNIX;std::string name="thorhaven.pad."+std::to_string(uid)+"."+nonce;memcpy(addr.sun_path+1,name.data(),name.size());if(bind(server,(sockaddr*)&addr,offsetof(sockaddr_un,sun_path)+1+name.size())||listen(server,1))return 3;
 signal(SIGTERM,signalStop);signal(SIGINT,signalStop);signal(SIGPIPE,SIG_IGN);pollfd pf{server,POLLIN,0};if(poll(&pf,1,8000)<=0){close(server);return 4;}int client=accept4(server,nullptr,nullptr,SOCK_CLOEXEC|SOCK_NONBLOCK);close(server);ucred peer{};socklen_t len=sizeof(peer);if(client<0||getsockopt(client,SOL_SOCKET,SO_PEERCRED,&peer,&len)||peer.uid!=(unsigned)uid){if(client>=0)close(client);return 5;}
 Pad pad;bool authorized=false;std::string buffer;long long last=Pad::now();while(alive&&Pad::now()-last<3500){pollfd fds[2]={{client,POLLIN,0},{pad.source,POLLIN,0}};int n=poll(fds,2,100);if(n<0&&errno!=EINTR)break;if(fds[0].revents&(POLLHUP|POLLERR))break;
 if(fds[0].revents&POLLIN){char bytes[1024];int count=read(client,bytes,sizeof(bytes));if(count<=0)break;buffer.append(bytes,count);if(buffer.size()>4096)break;size_t pos;while((pos=buffer.find('\n'))!=std::string::npos){std::string line=buffer.substr(0,pos);buffer.erase(0,pos+1);std::istringstream s(line);std::string cmd,response;s>>cmd;
 if(!authorized){std::string token;s>>token;if(cmd!="AUTH"||token!=nonce){alive=0;break;}authorized=true;response="OK Verbonden";}else if(cmd=="PING")response="OK";else if(cmd=="START"){int idx=-1;s>>idx;response=pad.start(idx);}else if(cmd=="CONFIG")response=pad.config(s);else if(cmd=="STOP"){pad.stop();response="OK Originele controller hersteld";}else if(cmd=="STATUS")response=pad.source>=0?"OK actief":"OK gestopt";else if(cmd=="QUIT"){pad.stop();alive=0;response="OK Gestopt";}else response="ERR command";
 response+='\n';if(write(client,response.data(),response.size())!=(ssize_t)response.size()){alive=0;break;}last=Pad::now();}}
 if(pad.source>=0&&(fds[1].revents&(POLLERR|POLLHUP)))pad.stop();else if(pad.source>=0&&(fds[1].revents&POLLIN)&&!pad.events())pad.stop();if(pad.emergency&&Pad::now()-pad.emergency>=3000)pad.stop();}
 pad.stop();close(client);return 0;}
static int tests(){input_absinfo r{};r.minimum=-32768;r.maximum=32767;int checks=0;auto check=[&](bool b){if(!b)exit(10+checks);checks++;};check(std::abs(transform(1000,r,r,10,false,0))<=1);check(transform(32767,r,r,10,false,0)==32767);check(transform(32767,r,r,10,true,0)==-32768);check(transform(16384,r,r,0,false,1)<transform(16384,r,r,0,false,0));check(transform(16384,r,r,0,false,2)>transform(16384,r,r,0,false,0));check(!targetOK(999));Pad p;std::istringstream bad("41 10 0 0 0");check(p.config(bad).find("ERR")==0);check(p.left==10);std::istringstream valid("20 15 3 1 2 305 304 307 308 310 311 312 313 314 315 316 317 318 544 545 546 547");check(p.config(valid).find("OK")==0);check(p.map[304]==305&&p.swap==1);std::cout<<"PASS "<<checks<<" native checks\n";return 0;}
int main(int argc,char**argv){if(argc==2&&!strcmp(argv[1],"--probe")){probe();return 0;}if(argc==2&&!strcmp(argv[1],"--selftest"))return tests();if(argc==4&&!strcmp(argv[1],"--serve")){char*end;long uid=strtol(argv[2],&end,10);if(*end||uid<10000||uid>2000000)return 2;return serve(uid,argv[3]);}return 1;}
