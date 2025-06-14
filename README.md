# 프로젝트 로컬 실행 방법

## 사전 준비
* [Docker](https://www.docker.com/products/docker-desktop/) 설치
* Java 17 설치

## 실행 순서
1.  **저장소 클론**
    ```bash
    git clone https://github.com/Hunseokc/email-local
    cd [프로젝트 폴더]
    ```

2.  **환경 변수 설정**
    * 프로젝트 루트 폴더의 `.env.example` 파일을 복사하여 `.env` 파일을 생성합니다.
    * `.env` 파일을 열어 각 API 키를 자신의 키로 채워넣습니다. (테스트를 위해 무료 등급 키를 발급받으시면 됩니다.)
      * 단, OpenAI API 테스트 과정에서 무료 티어 계정 Token 제한으로 인해 오류가 발생할 수 있습니다 (실제 동작은 웹 서비스 링크로 확인)

3.  **Spring Boot 애플리케이션 실행(app + db docker 컨테이너 실행)**
    * 프로젝트 루트 폴더에서 아래 명령어를 실행하여 Docker 컨테이너를 시작합니다. (실제 프로젝트는 supabase와 redis cloud를 사용하였습니다)
    ```bash
    docker-compose up --build
    ```

4. **접속 확인**
    * 웹 브라우저에서 `http://localhost:8080`으로 접속하여 애플리케이션을 확인합니다.

## 라이브 데모
* 실제 배포된 서비스는 [http://your-ec2-address.com](http://your-ec2-address.com) 에서 확인하실 수 있습니다.